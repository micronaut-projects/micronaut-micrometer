/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.micrometer.logging;

import javax.inject.Singleton;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The LoggingMeterRegistryFactory that will configure and create a logging meter registry.
 */
@Factory
public class LoggingMeterRegistryFactory {
    public static final String LOGGING_CONFIG = MICRONAUT_METRICS_EXPORT + ".logging";
    public static final String LOGGING_ENABLED = LOGGING_CONFIG + ".enabled";

    private final LoggingRegistryConfig loggingConfig;

    /**
     * Sets the underlying logging meter registry properties.
     *
     * @param loggingConfig logging meter properties
     */
    LoggingMeterRegistryFactory(LoggingRegistryConfigurationProperties loggingConfig) {
        this.loggingConfig = loggingConfig == null ? LoggingRegistryConfig.DEFAULT : loggingConfig;
    }

    /**
     * Create a LoggingMeterRegistry bean if global metrics are enabled
     * and the logging flag is enabled.  Will be false by default when this
     * configuration is included in project.
     *
     * @return A LoggingMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = LOGGING_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
    @Requires(beans = CompositeMeterRegistry.class)
    LoggingMeterRegistry loggingMeterRegistry() {
        return new LoggingMeterRegistry(loggingConfig, Clock.SYSTEM);
    }
}
