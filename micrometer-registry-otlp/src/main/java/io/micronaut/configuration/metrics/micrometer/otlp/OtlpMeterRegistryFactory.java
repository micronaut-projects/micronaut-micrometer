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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.registry.otlp.ExemplarContextProvider;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.registry.otlp.OtlpMetricsSender;
import io.micrometer.tracing.Tracer;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Properties;
import java.util.concurrent.ThreadFactory;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates an OTLP meter registry.
 */
@Factory
public class OtlpMeterRegistryFactory {

    public static final String OTLP_CONFIG = MICRONAUT_METRICS_EXPORT + ".otlp";
    public static final String OTLP_ENABLED = OTLP_CONFIG + ".enabled";

    /**
     * The name of the {@link ThreadFactory} bean that the OTLP registry publishes from, when there is one.
     *
     * @since 6.2.0
     */
    public static final String OTLP_THREAD_FACTORY = "otlp";

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
     * Create a OtlpMeterRegistry bean if global metrics are enabled.
     *
     * @param exportConfigurationProperties The export configuration
     * @param clock The clock, or {@code null} to use the system clock
     * @param metricsSender The sender of the metrics, or {@code null} to send them over HTTP
     * @param exemplarContextProvider The provider of the exemplar context, or {@code null} for no exemplars
     * @param threadFactory The thread factory that the registry publishes from, or {@code null} for the default one
     * @return OtlpMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, notEquals = StringUtils.FALSE)
    @Requires(beans = CompositeMeterRegistry.class)
    OtlpMeterRegistry otlpMeterRegistry(ExportConfigurationProperties exportConfigurationProperties,
                                        @Nullable Clock clock,
                                        @Nullable OtlpMetricsSender metricsSender,
                                        @Nullable ExemplarContextProvider exemplarContextProvider,
                                        @Named(OTLP_THREAD_FACTORY) @Nullable ThreadFactory threadFactory) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        OtlpMeterRegistry.Builder builder = OtlpMeterRegistry.builder(exportConfig::getProperty)
            .clock(clock != null ? clock : Clock.SYSTEM);
        if (metricsSender != null) {
            builder.metricsSender(metricsSender);
        }
        if (exemplarContextProvider != null) {
            builder.exemplarContextProvider(exemplarContextProvider);
        }
        if (threadFactory != null) {
            builder.threadFactory(threadFactory);
        }
        return builder.build();
    }

    /**
     * Creates the provider of the exemplar context, from the current Micrometer Tracing span.
     *
     * @param tracer The tracer
     * @param include Which spans exemplars are recorded for, {@code sampled-traces} or {@code all}
     * @return The exemplar context provider
     * @since 6.2.0
     */
    @Singleton
    @Secondary
    @Requires(classes = Tracer.class)
    @Requires(beans = Tracer.class)
    @Requires(property = EXEMPLARS_ENABLED, notEquals = StringUtils.FALSE)
    ExemplarContextProvider micrometerTracingExemplarContextProvider(Tracer tracer,
                                                                     @Property(name = EXEMPLARS_INCLUDE, defaultValue = "sampled-traces") String include) {
        return new MicrometerTracingExemplarContextProvider(tracer, "all".equalsIgnoreCase(include));
    }
}
