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
package io.micronaut.configuration.metrics.micrometer.stackdriver;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.stackdriver.StackdriverConfig;
import io.micrometer.stackdriver.StackdriverMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The StackdriverMeterRegistryFactory that will configure and create a wavefront meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class StackdriverMeterRegistryFactory {

    public static final String STACKDRIVER_CONFIG = MICRONAUT_METRICS_EXPORT + ".stackdriver";
    public static final String STACKDRIVER_ENABLED = STACKDRIVER_CONFIG + ".enabled";

    /**
     * Create a StackdriverMeterRegistry bean if global metrics are enables
     * and the stackdriver is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A StackdriverMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, notEquals = StringUtils.FALSE)
    @Requires(property = STACKDRIVER_ENABLED, notEquals = StringUtils.FALSE)
    @Requires(beans = CompositeMeterRegistry.class)
    StackdriverMeterRegistry stackdriverMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new StackdriverMeterRegistry(exportConfig::getProperty, Clock.SYSTEM);
    }

}
