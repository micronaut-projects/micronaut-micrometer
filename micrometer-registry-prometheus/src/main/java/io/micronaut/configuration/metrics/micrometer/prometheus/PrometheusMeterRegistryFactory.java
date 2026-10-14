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

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.StringUtils;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.tracer.common.SpanContext;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS;
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
     * Whether exemplars are recorded from the current Micrometer Tracing span when a {@link Tracer} bean exists.
     * Default {@code true}.
     *
     * @since 6.2.0
     */
    public static final String EXEMPLARS_ENABLED = MICRONAUT_METRICS + "exemplars.enabled";

    /**
     * Which spans exemplars are recorded for: {@code sampled-traces} (the default) for sampled spans only, or {@code all}
     * for every span.
     *
     * @since 6.2.0
     */
    public static final String EXEMPLARS_INCLUDE = MICRONAUT_METRICS + "exemplars.include";

    /**
     * Create a PrometheusMeterRegistry bean if global metrics are enabled
     * and Prometheus is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param prometheusConfig The Prometheus configuration
     * @param clock The clock, or {@code null} to use the system clock
     * @param spanContext The span context that exemplars are sampled from, or {@code null} for no exemplars
     * @return PrometheusMeterRegistry
     */
    @Singleton
    PrometheusMeterRegistry prometheusConfig(PrometheusConfig prometheusConfig,
                                             @Nullable Clock clock,
                                             @Nullable SpanContext spanContext) {
        return new PrometheusMeterRegistry(prometheusConfig, new PrometheusRegistry(),
            clock != null ? clock : Clock.SYSTEM, spanContext);
    }

    /**
     * Creates the span context that exemplars are sampled from, from the current Micrometer Tracing span.
     *
     * @param tracer The tracer
     * @param include Which spans exemplars are recorded for, {@code sampled-traces} or {@code all}
     * @return The span context
     * @since 6.2.0
     */
    @Singleton
    @Secondary
    @Requires(classes = Tracer.class)
    @Requires(beans = Tracer.class)
    @Requires(property = EXEMPLARS_ENABLED, notEquals = StringUtils.FALSE)
    SpanContext micrometerTracingSpanContext(Tracer tracer,
                                             @Property(name = EXEMPLARS_INCLUDE, defaultValue = "sampled-traces") String include) {
        return new MicrometerTracingSpanContext(tracer, "all".equalsIgnoreCase(include));
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
