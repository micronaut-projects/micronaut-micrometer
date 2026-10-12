/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.micrometer.intercept;

import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Copy-on-write cache of settings resolved from the annotations of an intercepted method.
 * Reads are lock free and keyed by {@link ExecutableMethod} identity, which avoids hashing the method signature on
 * every invocation.
 *
 * @param <V> The type of the cached settings
 */
final class MethodMetricsCache<V> {

    /**
     * Upper bound guarding against unbounded growth if executable methods are ever created per invocation.
     */
    private static final int MAX_ENTRIES = 10_000;

    private volatile Map<ExecutableMethod<?, ?>, V> entries = new IdentityHashMap<>();

    /**
     * @param method The method
     * @return The cached settings or {@code null}
     */
    @Nullable V get(ExecutableMethod<?, ?> method) {
        return entries.get(method);
    }

    /**
     * Caches the settings unless another thread has already cached settings for the method.
     *
     * @param method The method
     * @param value The settings
     * @return The cached settings
     */
    synchronized V put(ExecutableMethod<?, ?> method, V value) {
        Map<ExecutableMethod<?, ?>, V> current = entries;
        V existing = current.get(method);
        if (existing != null) {
            return existing;
        }
        if (current.size() < MAX_ENTRIES) {
            Map<ExecutableMethod<?, ?>, V> copy = new IdentityHashMap<>(current);
            copy.put(method, value);
            entries = copy;
        }
        return value;
    }
}
