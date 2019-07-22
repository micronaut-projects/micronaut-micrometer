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
package io.micronaut.configuration.metrics.micrometer.azuremonitor;

import io.micrometer.azuremonitor.AzureMonitorConfig;
import io.micrometer.azuremonitor.AzureMonitorMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;
import java.util.Properties;

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

    /**
     * Create a AzureMonitorMeterRegistry bean if global metrics are enables
     * and the azuremonitor is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A AzureMonitorMeterRegistry
     */
    @Singleton
    AzureMonitorMeterRegistry azureMonitorMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new AzureMonitorMeterRegistry(exportConfig::getProperty, Clock.SYSTEM);
    }

}
