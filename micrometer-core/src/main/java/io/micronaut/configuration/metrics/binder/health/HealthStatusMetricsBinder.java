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
package io.micronaut.configuration.metrics.binder.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.CurrentHealthStatus;
import io.micronaut.health.HealthStatus;
import jakarta.inject.Singleton;

import java.util.Locale;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.TRUE;

/**
 * Reports the current health status of the application as the {@code health.status} gauge:
 * {@code 3} for {@code UP}, {@code 2} for {@code OUT_OF_SERVICE}, {@code 1} for {@code DOWN} or
 * any other non-operational status, and {@code 0} for {@code UNKNOWN} or any other status.
 *
 * <p>The gauge reads {@link CurrentHealthStatus}, which the health monitor of the management
 * module ({@code micronaut.health.monitor.enabled}) keeps up to date. Reading the gauge does not
 * run the health indicators. Disabled by default.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Singleton
@RequiresMetrics
@Requires(classes = CurrentHealthStatus.class)
@Requires(beans = CurrentHealthStatus.class)
@Requires(property = HealthStatusMetricsBinder.ENABLED, value = TRUE)
public class HealthStatusMetricsBinder implements MeterBinder {

    /**
     * Whether the health status gauge is enabled.
     */
    public static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".health.enabled";

    /**
     * The gauge name.
     */
    public static final String HEALTH_STATUS = "health.status";

    private static final String OUT_OF_SERVICE = "OUT_OF_SERVICE";

    private final CurrentHealthStatus currentHealthStatus;

    /**
     * @param currentHealthStatus The current health status
     */
    public HealthStatusMetricsBinder(CurrentHealthStatus currentHealthStatus) {
        this.currentHealthStatus = currentHealthStatus;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(HEALTH_STATUS, currentHealthStatus, status -> toValue(status.current()))
            .description("The health status of the application: 3 = UP, 2 = OUT_OF_SERVICE, 1 = DOWN, 0 = UNKNOWN")
            .strongReference(true)
            .register(registry);
    }

    /**
     * @param status The health status
     * @return The gauge value of the status
     */
    static double toValue(HealthStatus status) {
        String name = status.getName().toUpperCase(Locale.ROOT);
        if (HealthStatus.NAME_UP.equals(name)) {
            return 3;
        }
        if (OUT_OF_SERVICE.equals(name)) {
            return 2;
        }
        if (HealthStatus.NAME_DOWN.equals(name) || !status.getOperational().orElse(true)) {
            return 1;
        }
        return 0;
    }
}
