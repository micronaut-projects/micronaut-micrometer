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
package io.micronaut.configuration.metrics.micrometer.influx;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The InfluxMeterRegistryFactory that will configure and create a influx meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class InfluxMeterRegistryFactory {

    public static final String INFLUX_CONFIG = MICRONAUT_METRICS_EXPORT + ".influx";
    public static final String INFLUX_ENABLED = INFLUX_CONFIG + ".enabled";

    private final InfluxConfig influxConfig;

    /**
     * Sets the underlying influx meter registry properties.
     *
     * @param influxDbConfigurationProperties prometheus properties
     */
    public InfluxMeterRegistryFactory(InfluxConfigurationProperties influxDbConfigurationProperties) {
        this.influxConfig = influxDbConfigurationProperties;
    }

    /**
     * Create a InfluxMeterRegistry bean if global metrics are enables
     * and the influx is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A InfluxMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = INFLUX_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    InfluxMeterRegistry influxConfig() {
        return new InfluxMeterRegistry(influxConfig, Clock.SYSTEM);
    }

}
