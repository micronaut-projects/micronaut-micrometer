/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.metrics.micrometer.dynatrace;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The DynatraceMeterRegistryFactory that will configure and create a kairos meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class DynatraceMeterRegistryFactory {

    public static final String DYNATRACE_CONFIG = MICRONAUT_METRICS_EXPORT + ".dynatrace";
    public static final String DYNATRACE_ENABLED = DYNATRACE_CONFIG + ".enabled";

    private final DynatraceConfig dynatraceConfig;

    /**
     * Sets the underlying new kairos meter registry properties.
     *
     * @param dynatraceConfigurationProperties dynatrace properties
     */
    public DynatraceMeterRegistryFactory(DynatraceConfigurationProperties dynatraceConfigurationProperties) {
        this.dynatraceConfig = dynatraceConfigurationProperties;
    }

    /**
     * Create a DynatraceMeterRegistry bean if global metrics are enables
     * and the dynatrace is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A DynatraceMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = DYNATRACE_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    DynatraceMeterRegistry dynatraceMeterRegistry() {
        return new DynatraceMeterRegistry(dynatraceConfig, Clock.SYSTEM);
    }

}
