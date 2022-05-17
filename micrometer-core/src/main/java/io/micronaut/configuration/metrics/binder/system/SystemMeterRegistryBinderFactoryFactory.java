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
package io.micronaut.configuration.metrics.binder.system;

import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Creates the system metrics beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@RequiresMetrics
public class SystemMeterRegistryBinderFactoryFactory {

    /**
     * Uptime metrics bean.
     *
     * @return uptimeMetrics bean
     */
    @Bean
    @Singleton
    @Primary
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".uptime.enabled", notEquals = FALSE)
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }

    /**
     * Processor metrics bean.
     *
     * @return processorMetrics bean
     */
    @Bean
    @Singleton
    @Primary
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".processor.enabled", notEquals = FALSE)
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * Files metrics bean.
     *
     * @return fileDescriptorMetrics bean
     */
    @Bean
    @Singleton
    @Primary
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".files.enabled", notEquals = FALSE)
    public FileDescriptorMetrics fileDescriptorMetrics() {
        return new FileDescriptorMetrics();
    }
}
