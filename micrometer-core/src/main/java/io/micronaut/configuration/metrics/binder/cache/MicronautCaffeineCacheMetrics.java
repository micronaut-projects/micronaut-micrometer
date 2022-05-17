/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.configuration.metrics.binder.cache;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micronaut.caffeine.cache.AsyncLoadingCache;
import io.micronaut.caffeine.cache.Cache;
import io.micronaut.caffeine.cache.LoadingCache;

import java.util.concurrent.TimeUnit;

/**
 * Collect metrics from Caffeine's com.github.benmanes.caffeine.cache.Cache.
 * <p>
 * Note that `recordStats()` is required to gather non-zero statistics:
 * <pre>{@code
 * Cache<String, String> cache = Caffeine.newBuilder().recordStats().build();
 * CaffeineCacheMetrics.monitor(registry, cache, "mycache", "region", "test");
 * }</pre>
 * <p>
 *
 * NOTE: This is a fork of https://github.com/micrometer-metrics/micrometer/blob/master/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/cache/CaffeineCacheMetrics.java
 *
 * The fork is needed because Micronaut repackages caffeine under a different package.
 *
 * @author Clint Checketts
 * @author graemerocher
 * @deprecated Use {@link io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics instead}. Since cache was separated from core this is no longer necessary
 */
@NonNullApi
@NonNullFields
@Deprecated
public class MicronautCaffeineCacheMetrics extends CacheMeterBinder {
    private final Cache<?, ?> cache;

    /**
     * Creates a new {@link MicronautCaffeineCacheMetrics} instance.
     *
     * @param cache     The cache to be instrumented. You must call {@link io.micronaut.caffeine.cache.Caffeine} prior to building the cache
     *                  for metrics to be recorded.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      tags to apply to all recorded metrics.
     */
    public MicronautCaffeineCacheMetrics(Cache<?, ?> cache, String cacheName, Iterable<Tag> tags) {
        super(cache, cacheName, tags);
        this.cache = cache;
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link io.micronaut.caffeine.cache.Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static <C extends Cache> C monitor(MeterRegistry registry, C cache, String cacheName, String... tags) {
        return monitor(registry, cache, cacheName, Tags.of(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link io.micronaut.caffeine.cache.Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see io.micronaut.caffeine.cache.stats.CacheStats
     */
    public static <C extends Cache> C monitor(MeterRegistry registry, C cache, String cacheName, Iterable<Tag> tags) {
        new MicronautCaffeineCacheMetrics(cache, cacheName, tags).bindTo(registry);
        return cache;
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link io.micronaut.caffeine.cache.Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static <C extends AsyncLoadingCache> C monitor(MeterRegistry registry, C cache, String cacheName, String... tags) {
        return monitor(registry, cache, cacheName, Tags.of(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link io.micronaut.caffeine.cache.Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see io.micronaut.caffeine.cache.stats.CacheStats
     */
    public static <C extends AsyncLoadingCache> C monitor(MeterRegistry registry, C cache, String cacheName, Iterable<Tag> tags) {
        monitor(registry, cache.synchronous(), cacheName, tags);
        return cache;
    }

    @Override
    protected Long size() {
        return cache.estimatedSize();
    }

    @Override
    protected long hitCount() {
        return cache.stats().hitCount();
    }

    @Override
    protected Long missCount() {
        return cache.stats().missCount();
    }

    @Override
    protected Long evictionCount() {
        return cache.stats().evictionCount();
    }

    @Override
    protected long putCount() {
        return cache.stats().loadCount();
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        Gauge.builder("cache.eviction.weight", cache, c -> c.stats().evictionWeight())
                .tags(getTagsWithCacheName())
                .description("The sum of weights of evicted entries. This total does not include manual invalidations.")
                .register(registry);

        if (cache instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            TimeGauge.builder("cache.load.duration", cache, TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime())
                    .tags(getTagsWithCacheName())
                    .description("The time the cache has spent loading new values")
                    .register(registry);

            FunctionCounter.builder("cache.load", cache, c -> c.stats().loadSuccessCount())
                    .tags(getTagsWithCacheName())
                    .tags("result", "success")
                    .description("The number of times cache lookup methods have successfully loaded a new value")
                    .register(registry);

            FunctionCounter.builder("cache.load", cache, c -> c.stats().loadFailureCount())
                    .tags(getTagsWithCacheName())
                    .tags("result", "failure")
                    .description("The number of times {@link Cache} lookup methods failed to load a new value, either " +
                            "because no value was found or an exception was thrown while loading")
                    .register(registry);
        }
    }
}
