/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.configuration.metrics.binder.jvm;

import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.NotInNativeImage;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Creates the virtual thread metrics bean when {@code io.micrometer:micrometer-java21} is on the
 * classpath.
 *
 * <p>{@link VirtualThreadMetrics} reads JDK Flight Recorder events through a recording stream,
 * which is closed when the context shuts down. It is not created in a native image.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Factory
@RequiresMetrics
@Requires(classes = VirtualThreadMetrics.class)
@Requires(condition = NotInNativeImage.class)
@Requires(property = VirtualThreadMetricsFactory.ENABLED, notEquals = FALSE)
public class VirtualThreadMetricsFactory {

    /**
     * Whether virtual thread metrics are enabled.
     */
    public static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".virtual-threads.enabled";

    /**
     * Virtual thread metrics bean ({@code jvm.threads.virtual.*}).
     *
     * @return virtualThreadMetrics
     */
    @Singleton
    @Bean(preDestroy = "close")
    public VirtualThreadMetrics virtualThreadMetrics() {
        return new VirtualThreadMetrics();
    }
}
