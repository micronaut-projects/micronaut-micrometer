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

package io.micronaut.configuration.metrics.micrometer.wavefront;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.wavefront.WavefrontConfig;
import io.micrometer.wavefront.WavefrontMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The WavefrontMeterRegistryFactory that will configure and create a wavefront meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class WavefrontMeterRegistryFactory {

    public static final String WAVEFRONT_CONFIG = MICRONAUT_METRICS_EXPORT + ".wavefront";
    public static final String WAVEFRONT_ENABLED = WAVEFRONT_CONFIG + ".enabled";

    private final WavefrontConfig wavefrontConfig;

    /**
     * Sets the underlying wavefront meter registry properties.
     *
     * @param wavefrontConfigurationProperties wavefront properties
     */
    public WavefrontMeterRegistryFactory(WavefrontConfigurationProperties wavefrontConfigurationProperties) {
        this.wavefrontConfig = wavefrontConfigurationProperties;
    }

    /**
     * Create a WavefrontMeterRegistry bean if global metrics are enables
     * and the wavefront is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A WavefrontMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = WAVEFRONT_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    WavefrontMeterRegistry wavefrontMeterRegistry() {
        return new WavefrontMeterRegistry(wavefrontConfig, Clock.SYSTEM);
    }

}
