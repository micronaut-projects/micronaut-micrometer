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
package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;

import javax.validation.constraints.NotNull;
import java.util.Collection;

/**
 * Default implementation of {@link MeterRegistryConfigurer} that adds the binders and filters
 * to the micrometer meter registry. This is specifically needed for the {@link io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint}
 *
 * @author Christian Oestreich
 * @since 1.0
 */
public class CompositeMeterRegistryConfigurer implements MeterRegistryConfigurer<CompositeMeterRegistry> {

    private final Collection<MeterBinder> binders;
    private final Collection<MeterFilter> filters;

    /**
     * Constructor for the micrometer meter registry configurer.
     *
     * @param binders binder beans
     * @param filters filter beans
     */
    public CompositeMeterRegistryConfigurer(
            Collection<MeterBinder> binders,
            Collection<MeterFilter> filters) {
        this.binders = binders;
        this.filters = filters;
    }

    /**
     * {@inheritDoc}
     * <p>
     * It is Important that filters are the first thing added so that subsequent operations are
     * appropriately filtered.
     *
     * @param meterRegistry Meter registry to bind metrics to.
     */
    @Override
    public void configure(@NotNull CompositeMeterRegistry meterRegistry) {
        addFilters(meterRegistry);
        addBinders(meterRegistry);
    }

    @Override
    public Class<CompositeMeterRegistry> getType() {
        return CompositeMeterRegistry.class;
    }

    /**
     * Add filters to the meter registry. More details available at https://micrometer.io/docs/concepts#_meter_filters
     *
     * @param meterRegistry the registry
     */
    private void addFilters(@NotNull CompositeMeterRegistry meterRegistry) {
        if (filters != null && !filters.isEmpty()) {
            filters.forEach(meterRegistry.config()::meterFilter);
        }
    }

    /**
     * Add binders to the meter registry. There are default binders available.
     * <p>
     * {@link io.micronaut.configuration.metrics.binder.jvm.JvmMeterRegistryBinderFactory}
     * {@link io.micronaut.configuration.metrics.binder.logging.LogbackMeterRegistryBinderFactory}
     * {@link io.micronaut.configuration.metrics.binder.system.SystemMeterRegistryBinderFactoryFactory}
     *
     * @param meterRegistry the meter registry
     */
    private void addBinders(@NotNull CompositeMeterRegistry meterRegistry) {
        if (binders != null && !binders.isEmpty()) {
            binders.forEach((binder) -> binder.bindTo(meterRegistry));
        }
    }
}
