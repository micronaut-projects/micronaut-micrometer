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

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.cache.Cache;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

import java.util.Collections;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * A cache Metrics binder for Micronaut's re-packaged version of Caffeine.
 *
 * @author graemerocher
 * @since 1.0
 * @deprecated Since cache was separated from core this is no longer necessary
 */
@Requires(property = MICRONAUT_METRICS_BINDERS + ".cache.enabled", notEquals = FALSE)
@Requires(classes = Cache.class)
@RequiresMetrics
@Singleton
@Deprecated
public class MicronautCaffeineCacheMetricsBinder implements BeanCreatedEventListener<Cache<?>> {

    private final BeanProvider<MeterRegistry> meterRegistryProvider;

    /**
     * Default constructor.
     *
     * @param meterRegistryProvider The meter registry provider
     */
    public MicronautCaffeineCacheMetricsBinder(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public Cache<?> onCreated(BeanCreatedEvent<Cache<?>> event) {
        MeterRegistry meterRegistry = meterRegistryProvider.get();
        Cache<?> cache = event.getBean();
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof io.micronaut.caffeine.cache.Cache) {
            MicronautCaffeineCacheMetrics.monitor(
                    meterRegistry,
                    (io.micronaut.caffeine.cache.Cache<?, ?>) nativeCache,
                    cache.getName(),
                    Collections.emptyList()
            );
        }
        return cache;
    }
}
