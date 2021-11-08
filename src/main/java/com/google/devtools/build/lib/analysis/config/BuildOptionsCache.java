// Copyright 2020 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.analysis.config;

import static com.google.common.base.Preconditions.checkNotNull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.value.AutoValue;
import java.util.function.BiFunction;

/**
 * Protects against excessive memory consumption when the same transition applies multiple times.
 *
 * <p>For example: an exec transition to {@code //my:exec_platform} for a tool that every rule in
 * the target configuration depends on.
 *
 * <p>Specifically, if {@code (origOptions1, context1)} produces {@code toOptions1}, {@code
 * (origOptions2, context2)} produces {@code toOptions2}, {@code origOptions1.equals(origOptions2)},
 * and {@code context1.equals(context2)}, this guarantees that {@code toOptions1 == toOptions2},
 * assuming the cache entry has not been evicted.
 *
 * <p>This means applying the same transition to the same source multiple times always returns the
 * same reference.
 *
 * <p>{@link BuildOptions} references are stored softly.
 */
public final class BuildOptionsCache<T> {

  private final Cache<CacheKey<T>, BuildOptions> cache = Caffeine.newBuilder().softValues().build();

  private final BiFunction<BuildOptionsView, T, BuildOptions> transition;

  public BuildOptionsCache(BiFunction<BuildOptionsView, T, BuildOptions> transition) {
    this.transition = checkNotNull(transition);
  }

  /**
   * Applies the given transition to the given {@code (fromOptions, context)} pair. Returns an
   * existing {@link BuildOptions} instance if one is already associated with that key. Else
   * constructs and caches a new {@link BuildOptions} instance using the given transition function.
   *
   * @param fromOptions the starting options
   * @param context an additional object that affects the transition's result
   */
  public BuildOptions applyTransition(BuildOptionsView fromOptions, T context) {
    return cache.get(
        CacheKey.create(fromOptions.underlying().checksum(), context),
        unused -> transition.apply(fromOptions, context));
  }

  /**
   * Helper class for matching ({@link BuildOptions}, {@link T}) cache keys by {@link
   * BuildOptions#checksum()}.
   *
   * @param <T> the type of the context object
   */
  @AutoValue
  abstract static class CacheKey<T> {
    abstract String checksum();

    abstract T context();

    static <T> CacheKey<T> create(String checksum, T context) {
      return new AutoValue_BuildOptionsCache_CacheKey<>(checksum, context);
    }
  }
}
