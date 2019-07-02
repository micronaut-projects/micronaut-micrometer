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

package io.micronaut.configuration.metrics.micrometer.humio;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.humio.HumioConfig;
import io.micrometer.humio.HumioMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The HumioMeterRegistryFactory that will configure and create a humio meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class HumioMeterRegistryFactory {

    public static final String HUMIO_CONFIG = MICRONAUT_METRICS_EXPORT + ".humio";
    public static final String HUMIO_ENABLED = HUMIO_CONFIG + ".enabled";

    private final HumioConfig humioConfig;

    /**
     * Sets the underlying Humio meter registry properties.
     *
     * @param humioConfigurationProperties humio properties
     */
    public HumioMeterRegistryFactory(HumioConfigurationProperties humioConfigurationProperties) {
        this.humioConfig = humioConfigurationProperties;
    }

    /**
     * Create a HumioMeterRegistry bean if global metrics are enables
     * and the humio is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A HumioMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = HUMIO_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    HumioMeterRegistry humioMeterRegistry() {
        return new HumioMeterRegistry(humioConfig, Clock.SYSTEM);
    }

}
