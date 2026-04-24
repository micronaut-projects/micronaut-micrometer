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
package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.TypeHint;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.Properties;

import static io.micronaut.core.annotation.TypeHint.AccessType.ALL_DECLARED_FIELDS;
import static io.micronaut.core.annotation.TypeHint.AccessType.ALL_DECLARED_METHODS;
import static io.micronaut.core.annotation.TypeHint.AccessType.ALL_PUBLIC_CONSTRUCTORS;
import static io.micrometer.core.instrument.Clock.SYSTEM;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Creates a StatsD meter registry.
 */
@Factory
@TypeHint(
    typeNames = {
        "io.micrometer.shaded.io.netty.buffer.AbstractByteBufAllocator",
        "io.micrometer.shaded.io.netty.util.ReferenceCountUtil",
        "io.micrometer.shaded.io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueColdProducerFields",
        "io.micrometer.shaded.io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueConsumerFields",
        "io.micrometer.shaded.io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueProducerFields",
        "io.micrometer.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueConsumerIndexField",
        "io.micrometer.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerIndexField",
        "io.micrometer.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerLimitField"
    },
    accessType = {
        ALL_PUBLIC_CONSTRUCTORS,
        ALL_DECLARED_METHODS,
        ALL_DECLARED_FIELDS
    }
)
public final class StatsdMeterRegistryFactory {

    public static final String STATSD_CONFIG = MICRONAUT_METRICS_EXPORT + ".statsd";
    public static final String STATSD_ENABLED = STATSD_CONFIG + ".enabled";

    /**
     * Create a StatsdMeterRegistry bean if global metrics are enabled
     * and StatsD is enabled. Will be true by default when this
     * configuration is included in project.
     * @param exportConfigurationProperties The export configuration
     * @return StatsdMeterRegistry
     */
    @Singleton
    @Bean(preDestroy = "close")
    @Requires(property = MICRONAUT_METRICS_ENABLED, notEquals = FALSE)
    @Requires(beans = CompositeMeterRegistry.class)
    StatsdMeterRegistry statsdMeterRegistry(ExportConfigurationProperties exportConfigurationProperties,
                                            @Nullable NativeImageUdpStatsdLineSink nativeImageUdpStatsdLineSink) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        StatsdConfig statsdConfig = exportConfig::getProperty;
        if (nativeImageUdpStatsdLineSink != null) {
            return StatsdMeterRegistry.builder(statsdConfig)
                .lineSink(nativeImageUdpStatsdLineSink)
                .clock(SYSTEM)
                .build();
        }
        return new StatsdMeterRegistry(statsdConfig, SYSTEM);
    }

    @Singleton
    @Bean(preDestroy = "close")
    @Requires(condition = NativeImageStatsdCondition.class)
    NativeImageUdpStatsdLineSink nativeImageUdpStatsdLineSink(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new NativeImageUdpStatsdLineSink(exportConfig::getProperty);
    }
}
