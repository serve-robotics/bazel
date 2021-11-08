// Copyright 2021 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.collect.nestedset;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.testing.GcFinalization;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.build.lib.bugreport.BugReporter;
import com.google.devtools.build.lib.collect.nestedset.NestedSetStore.FingerprintComputationResult;
import com.google.devtools.build.lib.collect.nestedset.NestedSetStore.MissingNestedSetException;
import com.google.protobuf.ByteString;
import java.lang.ref.WeakReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link NestedSetSerializationCache}. */
@RunWith(JUnit4.class)
public final class NestedSetSerializationCacheTest {

  private static final Object DEFAULT_CONTEXT = new Object();

  private final NestedSetSerializationCache cache =
      new NestedSetSerializationCache(BugReporter.defaultInstance());

  @Test
  public void putFutureIfAbsent_newFingerprint_returnsNull() {
    ByteString fingerprint1 = ByteString.copyFromUtf8("abc");
    ByteString fingerprint2 = ByteString.copyFromUtf8("xyz");
    SettableFuture<Object[]> future1 = SettableFuture.create();
    SettableFuture<Object[]> future2 = SettableFuture.create();

    assertThat(cache.putFutureIfAbsent(fingerprint1, future1, DEFAULT_CONTEXT)).isNull();
    assertThat(cache.putFutureIfAbsent(fingerprint2, future2, DEFAULT_CONTEXT)).isNull();
  }

  @Test
  public void putFutureIfAbsent_existingFingerprint_returnsExistingFuture() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future = SettableFuture.create();

    assertThat(cache.putFutureIfAbsent(fingerprint, future, DEFAULT_CONTEXT)).isNull();
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(future);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(future);
  }

  @Test
  public void putFutureIfAbsent_rejectsAlreadyDoneFuture() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future = SettableFuture.create();
    future.set(new Object[0]);

    assertThrows(
        IllegalArgumentException.class,
        () -> cache.putFutureIfAbsent(fingerprint, future, DEFAULT_CONTEXT));
  }

  @Test
  public void putFutureIfAbsent_futureCompletes_unwrapsContents() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future1 = SettableFuture.create();
    SettableFuture<Object[]> future2 = SettableFuture.create();
    Object[] contents = new Object[0];

    cache.putFutureIfAbsent(fingerprint, future1, DEFAULT_CONTEXT);
    future1.set(contents);

    assertThat(cache.putFutureIfAbsent(fingerprint, future2, DEFAULT_CONTEXT))
        .isSameInstanceAs(contents);
  }

  @Test
  public void putFutureIfAbsent_futureCompletes_cachesFingerprint() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future = SettableFuture.create();
    Object[] contents = new Object[0];

    cache.putFutureIfAbsent(fingerprint, future, DEFAULT_CONTEXT);
    future.set(contents);

    FingerprintComputationResult result = cache.fingerprintForContents(contents);
    assertThat(result.fingerprint()).isEqualTo(fingerprint);
    assertThat(result.writeStatus().isDone()).isTrue();
  }

  @Test
  public void putFutureIfAbsent_futureFails_notifiesBugReporter() {
    BugReporter mockBugReporter = mock(BugReporter.class);
    NestedSetSerializationCache cacheWithCustomBugReporter =
        new NestedSetSerializationCache(mockBugReporter);
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future = SettableFuture.create();
    Exception e = new MissingNestedSetException(fingerprint);

    cacheWithCustomBugReporter.putFutureIfAbsent(fingerprint, future, DEFAULT_CONTEXT);
    future.setException(e);

    verify(mockBugReporter).sendBugReport(e);
  }

  @Test
  public void putIfAbsent_newFingerprintAndContents_returnsNullAndCachesBothDirections() {
    ByteString fingerprint1 = ByteString.copyFromUtf8("abc");
    ByteString fingerprint2 = ByteString.copyFromUtf8("xyz");
    Object[] contents1 = new Object[] {"abc"};
    Object[] contents2 = new Object[] {"xyz"};
    FingerprintComputationResult result1 =
        FingerprintComputationResult.create(fingerprint1, SettableFuture.create());
    FingerprintComputationResult result2 =
        FingerprintComputationResult.create(fingerprint2, SettableFuture.create());

    assertThat(cache.putIfAbsent(contents1, result1, DEFAULT_CONTEXT)).isNull();
    assertThat(cache.putIfAbsent(contents2, result2, DEFAULT_CONTEXT)).isNull();
    assertThat(cache.fingerprintForContents(contents1)).isSameInstanceAs(result1);
    assertThat(cache.fingerprintForContents(contents2)).isSameInstanceAs(result2);
    assertThat(cache.putFutureIfAbsent(fingerprint1, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(contents1);
    assertThat(cache.putFutureIfAbsent(fingerprint2, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(contents2);
  }

  @Test
  public void putIfAbsent_existingFingerprintAndContents_returnsExistingResult() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    Object[] contents = new Object[0];
    FingerprintComputationResult result1 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());
    FingerprintComputationResult result2 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());
    FingerprintComputationResult result3 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());

    assertThat(cache.putIfAbsent(contents, result1, DEFAULT_CONTEXT)).isNull();
    assertThat(cache.putIfAbsent(contents, result2, DEFAULT_CONTEXT)).isSameInstanceAs(result1);
    assertThat(cache.putIfAbsent(contents, result3, DEFAULT_CONTEXT)).isSameInstanceAs(result1);
  }

  @Test
  public void putIfAbsent_calledDuringPendingDeserialization_overwritesFuture() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future = SettableFuture.create();
    Object[] contents = new Object[0];
    FingerprintComputationResult result =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());

    assertThat(cache.putFutureIfAbsent(fingerprint, future, DEFAULT_CONTEXT)).isNull();
    assertThat(cache.putIfAbsent(contents, result, DEFAULT_CONTEXT)).isNull();
    assertThat(cache.fingerprintForContents(contents)).isSameInstanceAs(result);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(contents);

    // After the future completes, the contents should still be cached (doesn't matter which array).
    Object[] deserializedContents = new Object[0];
    future.set(deserializedContents);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isAnyOf(contents, deserializedContents);

    // Both arrays should have a FingerprintComputationResult.
    FingerprintComputationResult resultForDeserializedContents =
        cache.fingerprintForContents(deserializedContents);
    assertThat(resultForDeserializedContents.fingerprint()).isEqualTo(fingerprint);
    assertThat(resultForDeserializedContents.writeStatus().isDone()).isTrue();
    assertThat(cache.fingerprintForContents(contents)).isSameInstanceAs(result);
  }

  @Test
  public void putFutureIfAbsent_cacheEntriesHaveLifetimeOfContents() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future = SettableFuture.create();

    cache.putFutureIfAbsent(fingerprint, future, DEFAULT_CONTEXT);

    // Before completing, still cached while future in memory.
    GcFinalization.awaitFullGc();
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(future);

    // After completing, still cached while contents in memory even if future is gone.
    WeakReference<SettableFuture<Object[]>> futureRef = new WeakReference<>(future);
    Object[] contents = new Object[0];
    future.set(contents);
    future = null;
    GcFinalization.awaitClear(futureRef);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(contents);
    FingerprintComputationResult result = cache.fingerprintForContents(contents);
    assertThat(result.fingerprint()).isEqualTo(fingerprint);
    assertThat(result.writeStatus().isDone()).isTrue();

    // Cleared after references are gone, and the cycle of putFutureIfAbsent starts over.
    WeakReference<Object[]> contentsRef = new WeakReference<>(contents);
    contents = null;
    GcFinalization.awaitClear(contentsRef);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isNull();
  }

  @Test
  public void putIfAbsent_cacheEntriesHaveLifetimeOfContents() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    Object[] contents = new Object[0];
    FingerprintComputationResult result =
        FingerprintComputationResult.create(fingerprint, immediateVoidFuture());

    cache.putIfAbsent(contents, result, DEFAULT_CONTEXT);

    // Still cached while in memory.
    GcFinalization.awaitFullGc();
    assertThat(cache.fingerprintForContents(contents)).isSameInstanceAs(result);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isSameInstanceAs(contents);

    // Cleared after references are gone, and the cycle of putFutureIfAbsent starts over.
    WeakReference<Object[]> ref = new WeakReference<>(contents);
    contents = null;
    GcFinalization.awaitClear(ref);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), DEFAULT_CONTEXT))
        .isNull();
  }

  @Test
  public void putFutureIfAbsent_usesContextToDistinguish() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    String contextLower = "lower";
    String contextUpper = "UPPER";
    SettableFuture<Object[]> futureLower = SettableFuture.create();
    SettableFuture<Object[]> futureUpper = SettableFuture.create();

    assertThat(cache.putFutureIfAbsent(fingerprint, futureLower, contextLower)).isNull();
    assertThat(cache.putFutureIfAbsent(fingerprint, futureUpper, contextUpper)).isNull();
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), contextLower))
        .isSameInstanceAs(futureLower);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), contextUpper))
        .isSameInstanceAs(futureUpper);

    Object[] contentsLower = new Object[] {"abc"};
    Object[] contentsUpper = new Object[] {"ABC"};
    futureLower.set(contentsLower);
    futureUpper.set(contentsUpper);

    FingerprintComputationResult resultLower = cache.fingerprintForContents(contentsLower);
    FingerprintComputationResult resultUpper = cache.fingerprintForContents(contentsUpper);
    assertThat(resultLower.fingerprint()).isEqualTo(fingerprint);
    assertThat(resultUpper.fingerprint()).isEqualTo(fingerprint);
    assertThat(resultLower.writeStatus().isDone()).isTrue();
    assertThat(resultUpper.writeStatus().isDone()).isTrue();
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), contextLower))
        .isSameInstanceAs(contentsLower);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), contextUpper))
        .isSameInstanceAs(contentsUpper);
  }

  @Test
  public void putIfAbsent_usesContextToDistinguish() {
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    String contextLower = "lower";
    String contextUpper = "UPPER";
    Object[] contentsLower = new Object[] {"abc"};
    Object[] contentsUpper = new Object[] {"ABC"};
    FingerprintComputationResult resultLower1 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());
    FingerprintComputationResult resultUpper1 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());
    FingerprintComputationResult resultLower2 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());
    FingerprintComputationResult resultUpper2 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());

    assertThat(cache.putIfAbsent(contentsLower, resultLower1, contextLower)).isNull();
    assertThat(cache.putIfAbsent(contentsUpper, resultUpper1, contextUpper)).isNull();
    assertThat(cache.putIfAbsent(contentsLower, resultLower2, contextLower))
        .isSameInstanceAs(resultLower1);
    assertThat(cache.putIfAbsent(contentsUpper, resultUpper2, contextUpper))
        .isSameInstanceAs(resultUpper1);

    assertThat(cache.fingerprintForContents(contentsLower)).isSameInstanceAs(resultLower1);
    assertThat(cache.fingerprintForContents(contentsUpper)).isSameInstanceAs(resultUpper1);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), contextLower))
        .isSameInstanceAs(contentsLower);
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), contextUpper))
        .isSameInstanceAs(contentsUpper);
  }

  @Test
  public void contextComparedByValueEquality() {
    class Context {
      @Override
      public int hashCode() {
        return 1;
      }

      @Override
      public boolean equals(Object o) {
        return o instanceof Context;
      }
    }
    ByteString fingerprint = ByteString.copyFromUtf8("abc");
    SettableFuture<Object[]> future = SettableFuture.create();
    Object[] contents = new Object[0];
    FingerprintComputationResult result1 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());
    FingerprintComputationResult result2 =
        FingerprintComputationResult.create(fingerprint, SettableFuture.create());

    assertThat(cache.putFutureIfAbsent(fingerprint, future, new Context())).isNull();
    assertThat(cache.putFutureIfAbsent(fingerprint, SettableFuture.create(), new Context()))
        .isSameInstanceAs(future);

    assertThat(cache.putIfAbsent(contents, result1, new Context())).isNull();
    assertThat(cache.putIfAbsent(contents, result2, new Context())).isSameInstanceAs(result1);
  }
}
