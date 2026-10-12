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
package io.micronaut.configuration.metrics.micrometer.prometheus;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.conventions.StringConvention;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a Prometheus meter registry.
 */
@Factory
public class PrometheusMeterRegistryFactory {

    public static final String PROMETHEUS_CONFIG = MICRONAUT_METRICS_EXPORT + ".prometheus";
    public static final String PROMETHEUS_ENABLED = PROMETHEUS_CONFIG + ".enabled";

    /**
     * The prefix of the Prometheus client properties, such as {@code io.prometheus.metrics.histogram_native_only},
     * which are passed to the Prometheus client with their keys unchanged.
     *
     * @since 6.2.0
     */
    public static final String PROMETHEUS_PROPERTIES = PROMETHEUS_CONFIG + ".properties";

    /**
     * Create a PrometheusMeterRegistry bean if global metrics are enabled
     * and Prometheus is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param prometheusConfig The Prometheus configuration
     * @return PrometheusMeterRegistry
     */
    @Singleton
    PrometheusMeterRegistry prometheusConfig(PrometheusConfig prometheusConfig) {
        return new PrometheusMeterRegistry(prometheusConfig);
    }

    /**
     * Creates the Prometheus configuration from {@code micronaut.metrics.export.prometheus}.
     *
     * @param exportConfigurationProperties The export configuration
     * @param environment The environment, used to read the Prometheus client properties
     * @return The Prometheus configuration
     * @since 6.2.0
     */
    @Singleton
    PrometheusConfig micronautPrometheusConfig(ExportConfigurationProperties exportConfigurationProperties,
                                               Environment environment) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        // the Prometheus client property keys are case sensitive, so they are read without changing their format
        Map<String, Object> clientProperties = environment.getProperties(PROMETHEUS_PROPERTIES, StringConvention.RAW);
        return new MicronautPrometheusConfig(exportConfig, clientProperties);
    }

    /**
     * A {@link PrometheusConfig} backed by the export configuration and the Prometheus client properties.
     *
     * @param exportConfig The export configuration
     * @param clientProperties The Prometheus client properties
     */
    private record MicronautPrometheusConfig(Properties exportConfig,
                                             Map<String, Object> clientProperties) implements PrometheusConfig {

        @Override
        public String get(String key) {
            return exportConfig.getProperty(key);
        }

        @Override
        public Properties prometheusProperties() {
            Properties properties = PrometheusConfig.super.prometheusProperties();
            if (properties == null) {
                properties = new Properties();
            }
            for (Map.Entry<String, Object> entry : clientProperties.entrySet()) {
                if (entry.getValue() != null) {
                    properties.setProperty(entry.getKey(), entry.getValue().toString());
                }
            }
            return properties;
        }
    }
}
