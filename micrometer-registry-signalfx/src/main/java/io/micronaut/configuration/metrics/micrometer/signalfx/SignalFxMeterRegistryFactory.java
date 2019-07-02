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

package io.micronaut.configuration.metrics.micrometer.signalfx;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.signalfx.SignalFxConfig;
import io.micrometer.signalfx.SignalFxMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The SignalFxMeterRegistryFactory that will configure and create a signalfx meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class SignalFxMeterRegistryFactory {

    public static final String SIGNALFX_CONFIG = MICRONAUT_METRICS_EXPORT + ".signalfx";
    public static final String SIGNALFX_ENABLED = SIGNALFX_CONFIG + ".enabled";

    private final SignalFxConfig signalFxConfig;

    /**
     * Sets the underlying signalfx meter registry properties.
     *
     * @param signalfxConfigurationProperties signalfx properties
     */
    public SignalFxMeterRegistryFactory(SignalFxConfigurationProperties signalfxConfigurationProperties) {
        this.signalFxConfig = signalfxConfigurationProperties;
    }

    /**
     * Create a StackdriverMeterRegistry bean if global metrics are enables
     * and the signalfx is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A SignalFxMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = SIGNALFX_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    SignalFxMeterRegistry signalFxMeterRegistry() {
        return new SignalFxMeterRegistry(signalFxConfig, Clock.SYSTEM);
    }

}
