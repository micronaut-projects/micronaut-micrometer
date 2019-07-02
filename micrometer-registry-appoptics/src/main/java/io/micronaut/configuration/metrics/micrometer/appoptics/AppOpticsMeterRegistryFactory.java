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
package io.micronaut.configuration.metrics.micrometer.appoptics;

import io.micrometer.appoptics.AppOpticsConfig;
import io.micrometer.appoptics.AppOpticsMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The AppOpticsMeterRegistryFactory that will configure and create a appoptics meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class AppOpticsMeterRegistryFactory {

    public static final String APPOPTICS_CONFIG = MICRONAUT_METRICS_EXPORT + ".appoptics";
    public static final String APPOPTICS_ENABLED = APPOPTICS_CONFIG + ".enabled";

    private final AppOpticsConfig appOpticsConfig;

    /**
     * Sets the underlying AppOptics meter registry properties.
     *
     * @param appopticsConfigurationProperties appoptics properties
     */
    public AppOpticsMeterRegistryFactory(AppOpticsConfigurationProperties appopticsConfigurationProperties) {
        this.appOpticsConfig = appopticsConfigurationProperties;
    }

    /**
     * Create a AppOpticsMeterRegistry bean if global metrics are enables
     * and the appoptics is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A AppOpticsMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = APPOPTICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    AppOpticsMeterRegistry appOpticsMeterRegistry() {
        return new AppOpticsMeterRegistry(appOpticsConfig, Clock.SYSTEM);
    }

}
