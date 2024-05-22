/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.configuration.metrics.micrometer.otlp;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

import java.util.Properties;

import static io.micrometer.core.instrument.Clock.SYSTEM;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Creates an OTLP meter registry.
 */
@Factory
public class OtlpMeterRegistryFactory {

    public static final String OTLP_CONFIG = MICRONAUT_METRICS_EXPORT + ".otlp";
    public static final String OTLP_ENABLED = OTLP_CONFIG + ".enabled";

    /**
     * Create a OtlpMeterRegistry bean if global metrics are enabled.
     * @param exportConfigurationProperties The export configuration
     * @return OtlpMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, notEquals = StringUtils.FALSE)
    @Requires(beans = CompositeMeterRegistry.class)
    OtlpMeterRegistry otlpMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new OtlpMeterRegistry(exportConfig::getProperty, SYSTEM);
    }
}
