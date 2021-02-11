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
package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.configuration.metrics.aggregator.CompositeMeterRegistryConfigurer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.CollectionUtils;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;

/**
 * Factory for all supported MetricRegistry beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@TypeHint(io.micrometer.core.instrument.MeterRegistry.class)
public class MeterRegistryFactory {

    public static final String MICRONAUT_METRICS = "micronaut.metrics.";
    public static final String MICRONAUT_METRICS_BINDERS = MICRONAUT_METRICS + "binders";
    public static final String MICRONAUT_METRICS_ENABLED = MICRONAUT_METRICS + "enabled";
    public static final String MICRONAUT_METRICS_EXPORT = MICRONAUT_METRICS + "export";

    /**
     * Create a CompositeMeterRegistry bean if metrics are enabled, true by default.
     *
     * @param registries The registries
     * @param configurers The configurers
     * @return A CompositeMeterRegistry
     */
    @Primary
    @Singleton
    @Bean(preDestroy = "close")
    CompositeMeterRegistry compositeMeterRegistry(List<MeterRegistry> registries,
                                                  List<MeterRegistryConfigurer<MeterRegistry>> configurers) {
        if (CollectionUtils.isEmpty(registries)) {
            registries.add(new SimpleMeterRegistry());
        }

        CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();
        for (MeterRegistry registry : registries) {
            compositeMeterRegistry.add(registry);
            for (MeterRegistryConfigurer<MeterRegistry> configurer : configurers) {
                // Let configurers configure the composite registry
                if (configurer.getType().isAssignableFrom(CompositeMeterRegistry.class) && configurer.supports(compositeMeterRegistry)) {
                    configurer.configure(compositeMeterRegistry);
                }

                //Let configurers configure individual registries
                if (configurer.getType().isAssignableFrom(registry.getClass()) && configurer.supports(registry)) {
                    configurer.configure(registry);
                }
            }
        }

        return compositeMeterRegistry;
    }

    /**
     * Creates a MeterRegistryConfigurer bean if the metrics are enabled, true by default.
     * <p>
     * This bean adds the filters and binders to the metric registry.
     *
     * @param binders list of binder beans
     * @param filters list of filter beans
     * @return meterRegistryConfigurer bean
     */
    @Primary
    @Singleton
    @RequiresMetrics
    MeterRegistryConfigurer<CompositeMeterRegistry> meterRegistryConfigurer(Collection<MeterBinder> binders,
                                                    Collection<MeterFilter> filters) {
        return new CompositeMeterRegistryConfigurer(binders, filters);
    }
}
