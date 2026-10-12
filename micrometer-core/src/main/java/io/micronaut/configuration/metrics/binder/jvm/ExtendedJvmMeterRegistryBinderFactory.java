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

import io.micrometer.core.instrument.binder.jvm.JvmCompilationMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadDeadlockMetrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.NotInNativeImage;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.core.util.StringUtils.TRUE;

/**
 * Creates the JVM metrics beans that complement those of {@link JvmMeterRegistryBinderFactory}.
 * They are disabled together with the other JVM metrics by
 * {@code micronaut.metrics.binders.jvm.enabled=false}.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Factory
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".jvm.enabled", notEquals = FALSE)
public class ExtendedJvmMeterRegistryBinderFactory {

    /**
     * The prefix of the JVM binder properties.
     */
    public static final String JVM_BINDERS = MICRONAUT_METRICS_BINDERS + ".jvm";

    /**
     * JVM info metrics bean ({@code jvm.info}).
     *
     * @return jvmInfoMetrics
     */
    @Singleton
    @Requires(property = JVM_BINDERS + ".info.enabled", notEquals = FALSE)
    public JvmInfoMetrics jvmInfoMetrics() {
        return new JvmInfoMetrics();
    }

    /**
     * JVM JIT compilation metrics bean ({@code jvm.compilation.time}). The binder registers no
     * meter when the JVM does not report compilation time, such as in a native image.
     *
     * @return jvmCompilationMetrics
     */
    @Singleton
    @Requires(property = JVM_BINDERS + ".compilation.enabled", notEquals = FALSE)
    public JvmCompilationMetrics jvmCompilationMetrics() {
        return new JvmCompilationMetrics();
    }

    /**
     * JVM heap pressure metrics bean ({@code jvm.gc.overhead} and {@code jvm.memory.usage.after.gc}).
     * It listens to garbage collection notifications, which a native image does not emit, so it is
     * not created in a native image.
     *
     * @return jvmHeapPressureMetrics
     */
    @Singleton
    @Bean(preDestroy = "close")
    @Requires(condition = NotInNativeImage.class)
    @Requires(property = JVM_BINDERS + ".heap-pressure.enabled", notEquals = FALSE)
    public JvmHeapPressureMetrics jvmHeapPressureMetrics() {
        return new JvmHeapPressureMetrics();
    }

    /**
     * JVM thread deadlock metrics bean ({@code jvm.threads.deadlocked} and
     * {@code jvm.threads.deadlocked.monitor}). Disabled by default, because each read of the gauges
     * searches the JVM for deadlocked threads.
     *
     * @return jvmThreadDeadlockMetrics
     */
    @Singleton
    @Requires(property = JVM_BINDERS + ".deadlock.enabled", value = TRUE)
    public JvmThreadDeadlockMetrics jvmThreadDeadlockMetrics() {
        return new JvmThreadDeadlockMetrics();
    }
}
