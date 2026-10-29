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
package io.micronaut.configuration.metrics.binder.datasource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instruments Micronaut related JDBC pool metrics via Micrometer.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".jdbc.enabled", notEquals = FALSE)
@Requires(beans = DataSourcePoolMetadata.class)
public class DataSourcePoolMetricsBinderFactory {

    private final List<MeterRegistry> pendingRegistries = new ArrayList<>();
    private boolean started;

    @Singleton
    final MeterBinder dataSourceMeterBinder(BeanContext context) {
        return registry -> {
            synchronized (pendingRegistries) {
                if (!started) {
                    pendingRegistries.add(registry);
                    return;
                }
            }
            bind(context, registry);
        };
    }

    @EventListener
    final void onStartup(StartupEvent event) {
        BeanContext context = event.getSource();
        // Finish registry creation before pool initialization can make measured HTTP calls.
        context.getBeansOfType(MeterRegistry.class);
        List<MeterRegistry> registries;
        synchronized (pendingRegistries) {
            started = true;
            registries = List.copyOf(pendingRegistries);
            pendingRegistries.clear();
        }
        for (MeterRegistry registry : registries) {
            bind(context, registry);
        }
    }

    private void bind(BeanContext context, MeterRegistry registry) {
        for (BeanRegistration<DataSourcePoolMetadata> registration : context.getBeanRegistrations(DataSourcePoolMetadata.class)) {
            dataSourceMeterBinder(registration.getIdentifier().getName(), registration.getBean()).bindTo(registry);
        }
    }

    /**
     * Creates a binder for a datasource.
     *
     * @param dataSourceName         The parameterized name of the datasource
     * @param dataSourcePoolMetadata The datasource metadata object to use for the binder
     * @return The pool metrics binder
     */
    public MeterBinder dataSourceMeterBinder(String dataSourceName,
                                             DataSourcePoolMetadata dataSourcePoolMetadata) {
        return new DataSourcePoolMetricsBinder(dataSourcePoolMetadata.getDataSource(),
                dataSourcePoolMetadata,
                dataSourceName,
                Collections.emptyList());
    }
}
