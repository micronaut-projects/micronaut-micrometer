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

package io.micronaut.configuration.metrics.micrometer.azuremonitor;

import io.micrometer.azuremonitor.AzureMonitorConfig;
import io.micrometer.azuremonitor.AzureMonitorMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The AzureMonitorMeterRegistryFactory that will configure and create a azuremonitor meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class AzureMonitorMeterRegistryFactory {

    public static final String AZUREMONITOR_CONFIG = MICRONAUT_METRICS_EXPORT + ".azuremonitor";
    public static final String AZUREMONITOR_ENABLED = AZUREMONITOR_CONFIG + ".enabled";

    private final AzureMonitorConfig azureMonitorConfig;

    /**
     * Sets the underlying Azure Monitor meter registry properties.
     *
     * @param azureMonitorConfigurationProperties azuremonitor properties
     */
    public AzureMonitorMeterRegistryFactory(AzureMonitorConfigurationProperties azureMonitorConfigurationProperties) {
        this.azureMonitorConfig = azureMonitorConfigurationProperties;
    }

    /**
     * Create a AzureMonitorMeterRegistry bean if global metrics are enables
     * and the azuremonitor is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A AzureMonitorMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = AZUREMONITOR_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    AzureMonitorMeterRegistry azureMonitorMeterRegistry() {
        return new AzureMonitorMeterRegistry(azureMonitorConfig, Clock.SYSTEM);
    }

}
