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
package io.micronaut.configuration.metrics.micrometer.wavefront;

import io.micrometer.wavefront.WavefrontMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Properties;

import static io.micrometer.core.instrument.Clock.SYSTEM;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a Wavefront meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class WavefrontMeterRegistryFactory {

    public static final String WAVEFRONT_CONFIG = MICRONAUT_METRICS_EXPORT + ".wavefront";
    public static final String WAVEFRONT_ENABLED = WAVEFRONT_CONFIG + ".enabled";

    /**
     * Create a WavefrontMeterRegistry bean if global metrics are enabled
     * and Wavefront is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param exportConfigurationProperties The export configuration
     * @return WavefrontMeterRegistry
     */
    @Singleton
    WavefrontMeterRegistry wavefrontMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new WavefrontMeterRegistry(exportConfig::getProperty, SYSTEM);
    }
}
