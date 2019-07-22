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
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.newrelic.NewRelicConfig;
import io.micrometer.newrelic.NewRelicMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The NewRelicMeterRegistryFactory that will configure and create a signalfx meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class NewRelicMeterRegistryFactory {

    public static final String NEWRELIC_CONFIG = MICRONAUT_METRICS_EXPORT + ".newrelic";
    public static final String NEWRELIC_ENABLED = NEWRELIC_CONFIG + ".enabled";

    /**
     * Create a NewRelicMeterRegistry bean if global metrics are enables
     * and the newrelic is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A NewRelicMeterRegistry
     */
    @Singleton
    NewRelicMeterRegistry newRelicMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new NewRelicMeterRegistry(exportConfig::getProperty, Clock.SYSTEM);
    }

}
