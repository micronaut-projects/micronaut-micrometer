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
import io.micrometer.core.instrument.binder.cache.JCacheMetrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.CacheManager;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instruments the active JCache manager.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Singleton
@RequiresMetrics
@Requires(beans = CacheManager.class)
@Requires(property = MICRONAUT_METRICS_BINDERS + ".cache.enabled", notEquals = FALSE)
public class JCacheMetricsBinder implements BeanCreatedEventListener<CacheManager> {

    private static final Logger LOG = LoggerFactory.getLogger(JCacheMetricsBinder.class);
    private final BeanProvider<MeterRegistry> meterRegistryProvider;

    /**
     * Default constructor.
     *
     * @param meterRegistryProvider The meter registry.
     */
    protected JCacheMetricsBinder(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public CacheManager onCreated(BeanCreatedEvent<CacheManager> event) {
        final MeterRegistry meterRegistry = meterRegistryProvider.get();
        final CacheManager cacheManager = event.getBean();
        for (String cacheName : cacheManager.getCacheNames()) {

            try {
                JCacheMetrics.monitor(
                        meterRegistry,
                        cacheManager.getCache(cacheName)
                );
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Unable to instrument JCache CacheManager with metrics: " + e.getMessage(), e);
                }
            }
        }
        return cacheManager;
    }
}
