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

package io.micronaut.configuration.metrics.micrometer.newrelic;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.newrelic.NewRelicConfig;
import io.micrometer.newrelic.NewRelicMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The NewRelicMeterRegistryFactory that will configure and create a signalfx meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class NewRelicMeterRegistryFactory {

    public static final String NEWRELIC_CONFIG = MICRONAUT_METRICS_EXPORT + ".newrelic";
    public static final String NEWRELIC_ENABLED = NEWRELIC_CONFIG + ".enabled";

    private final NewRelicConfig newRelicConfig;

    /**
     * Sets the underlying new relic meter registry properties.
     *
     * @param newRelicConfigurationProperties newrelic properties
     */
    public NewRelicMeterRegistryFactory(NewRelicConfigurationProperties newRelicConfigurationProperties) {
        this.newRelicConfig = newRelicConfigurationProperties;
    }

    /**
     * Create a NewRelicMeterRegistry bean if global metrics are enables
     * and the newrelic is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A StackdriverMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = NEWRELIC_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    NewRelicMeterRegistry newRelicMeterRegistry() {
        return new NewRelicMeterRegistry(newRelicConfig, Clock.SYSTEM);
    }

}
