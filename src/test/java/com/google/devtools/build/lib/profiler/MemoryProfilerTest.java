// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.profiler;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.ByteStreams;
import com.google.devtools.build.lib.profiler.MemoryProfiler.MemoryProfileStableHeapParameters;
import com.google.devtools.build.lib.profiler.MemoryProfiler.Sleeper;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for {@link MemoryProfiler}. */
@RunWith(JUnit4.class)
public class MemoryProfilerTest {
  @Test
  public void profilerDoesOneGcAndNoSleepNormally() throws Exception {
    MemoryProfiler profiler = MemoryProfiler.instance();
    profiler.setStableMemoryParameters(
        new MemoryProfileStableHeapParameters.Converter().convert("1,10"));
    profiler.start(ByteStreams.nullOutputStream());
    MemoryMXBean bean = Mockito.mock(MemoryMXBean.class);
    MemoryUsage heapUsage = new MemoryUsage(0, 0, 0, 0);
    MemoryUsage nonHeapUsage = new MemoryUsage(5, 5, 5, 5);
    when(bean.getHeapMemoryUsage()).thenReturn(heapUsage);
    when(bean.getNonHeapMemoryUsage()).thenReturn(nonHeapUsage);
    RecordingSleeper sleeper = new RecordingSleeper();
    MemoryProfiler.HeapAndNonHeap result =
        profiler.prepareBeanAndGetLocalMinUsage(ProfilePhase.ANALYZE, bean, sleeper);
    assertThat(result.getHeap()).isSameInstanceAs(heapUsage);
    assertThat(result.getNonHeap()).isSameInstanceAs(nonHeapUsage);
    assertThat(sleeper.sleeps).isEmpty();
    verify(bean, times(1)).gc();
    profiler.prepareBeanAndGetLocalMinUsage(ProfilePhase.FINISH, bean, sleeper);
    verify(bean, times(2)).gc();
    assertThat(sleeper.sleeps).isEmpty();
  }

  @Test
  public void profilerDoesOneGcAndNoSleepExceptInFinish() throws Exception {
    MemoryProfiler profiler = MemoryProfiler.instance();
    profiler.setStableMemoryParameters(
        new MemoryProfileStableHeapParameters.Converter().convert("3,10"));
    profiler.start(ByteStreams.nullOutputStream());
    MemoryMXBean bean = Mockito.mock(MemoryMXBean.class);
    MemoryUsage emptyHeap = new MemoryUsage(0, 0, 0, 0);
    MemoryUsage emptyNonHeap = new MemoryUsage(0, 0, 0, 0);
    when(bean.getHeapMemoryUsage()).thenReturn(emptyHeap);
    when(bean.getNonHeapMemoryUsage()).thenReturn(emptyNonHeap);
    RecordingSleeper sleeper = new RecordingSleeper();
    MemoryProfiler.HeapAndNonHeap result =
        profiler.prepareBeanAndGetLocalMinUsage(ProfilePhase.ANALYZE, bean, sleeper);
    assertThat(result.getHeap()).isSameInstanceAs(emptyHeap);
    assertThat(result.getNonHeap()).isSameInstanceAs(emptyNonHeap);
    assertThat(sleeper.sleeps).isEmpty();
    verify(bean, times(1)).gc();
    verify(bean, times(1)).getHeapMemoryUsage();
    verify(bean, times(1)).getNonHeapMemoryUsage();
    MemoryUsage heapUsage = new MemoryUsage(0, 1, 2, 2);
    when(bean.getHeapMemoryUsage())
        .thenReturn(new MemoryUsage(5, 5, 5, 5), heapUsage, new MemoryUsage(10, 1, 10, 10));
    MemoryUsage nonHeapUsage = new MemoryUsage(2, 2, 2, 2);
    when(bean.getNonHeapMemoryUsage())
        .thenReturn(new MemoryUsage(1, 1, 1, 1), nonHeapUsage, new MemoryUsage(2, 2, 2, 2));
    result = profiler.prepareBeanAndGetLocalMinUsage(ProfilePhase.FINISH, bean, sleeper);
    assertThat(result.getHeap()).isSameInstanceAs(heapUsage);
    assertThat(result.getNonHeap()).isSameInstanceAs(nonHeapUsage);
    assertThat(sleeper.sleeps)
        .containsExactly(Duration.ofSeconds(10), Duration.ofSeconds(10))
        .inOrder();
    verify(bean, times(4)).gc();
    verify(bean, times(4)).getHeapMemoryUsage();
    // Avoid call when heap usage is not minimal.
    verify(bean, times(3)).getNonHeapMemoryUsage();
  }

  private static class RecordingSleeper implements Sleeper {
    private final List<Duration> sleeps = new ArrayList<>();

    @Override
    public void sleep(Duration duration) {
      sleeps.add(duration);
    }
  }
}
