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
package io.micronaut.configuration.metrics.micrometer.newrelic;

import io.micrometer.core.instrument.Clock;
import io.micrometer.newrelic.NewRelicConfig;
import io.micrometer.newrelic.NewRelicMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a New Relic meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class NewRelicMeterRegistryFactory {

    public static final String NEWRELIC_CONFIG = MICRONAUT_METRICS_EXPORT + ".newrelic";
    public static final String NEWRELIC_ENABLED = NEWRELIC_CONFIG + ".enabled";

    /**
     * Create a NewRelicMeterRegistry bean if global metrics are enabled
     * and New Relic is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param newRelicConfig the New Relic config
     * @return NewRelicMeterRegistry
     */
    @Singleton
    NewRelicMeterRegistry newRelicMeterRegistry(NewRelicConfig newRelicConfig) {
        return new NewRelicMeterRegistry(newRelicConfig, Clock.SYSTEM);
    }

    /**
     * The New Relic config bean.
     *
     * @param exportConfigurationProperties The properties
     * @return the New Relic bean
     */
    @Singleton
    NewRelicConfig newRelicConfig(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return exportConfig::getProperty;
    }
}
