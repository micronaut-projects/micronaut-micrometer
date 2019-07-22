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
package io.micronaut.configuration.metrics.micrometer.logging;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;
import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The LoggingMeterRegistryFactory that will configure and create a logging meter registry.
 */
@Factory
public class LoggingMeterRegistryFactory {

    public static final String LOGGING_CONFIG = MICRONAUT_METRICS_EXPORT + ".logging";
    public static final String LOGGING_ENABLED = LOGGING_CONFIG + ".enabled";

    /**
     * Create a LoggingMeterRegistry bean if global metrics are enabled
     * and the logging flag is enabled.  Will be false by default when this
     * configuration is included in project.
     *
     * @return A LoggingMeterRegistry
     */
    @Singleton
    LoggingMeterRegistry loggingMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new LoggingMeterRegistry(exportConfig::getProperty, Clock.SYSTEM);
    }
}
