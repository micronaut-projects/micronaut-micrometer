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
package io.micronaut.configuration.metrics.micrometer.ganglia;

import io.micrometer.core.instrument.Clock;
import io.micrometer.ganglia.GangliaMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a Ganglia meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class GangliaMeterRegistryFactory {

    public static final String GANGLIA_CONFIG = MICRONAUT_METRICS_EXPORT + ".ganglia";
    public static final String GANGLIA_ENABLED = GANGLIA_CONFIG + ".enabled";

    /**
     * Create a GangliaMeterRegistry bean if global metrics are enabled
     * and Ganglia is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param exportConfigurationProperties The export configuration
     * @return GangliaMeterRegistry
     */
    @Singleton
    GangliaMeterRegistry gangliaMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new GangliaMeterRegistry(exportConfig::getProperty, Clock.SYSTEM);
    }

}
