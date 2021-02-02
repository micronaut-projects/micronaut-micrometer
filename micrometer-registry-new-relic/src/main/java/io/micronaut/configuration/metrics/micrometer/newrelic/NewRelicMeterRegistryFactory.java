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
package io.micronaut.configuration.metrics.micrometer.newrelic;

import com.newrelic.telemetry.Attributes;
import com.newrelic.telemetry.micrometer.NewRelicRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.newrelic.NewRelicConfig;
import io.micrometer.newrelic.NewRelicMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

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
    public static final String NEWRELIC_METRIC_API = NEWRELIC_CONFIG + ".metricAPI";

    /**
     * Create a NewRelicMeterRegistry bean if global metrics are enables
     * and the newrelic is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @param newRelicConfig The the new relic config
     * @return A NewRelicMeterRegistry
     */
    @Singleton
    @Requires(missingProperty =  NEWRELIC_METRIC_API)
    NewRelicMeterRegistry newRelicMeterRegistry(NewRelicConfig newRelicConfig) {
        return new NewRelicMeterRegistry(newRelicConfig, Clock.SYSTEM);
    }

    /**
     * The new relic config bean.
     * @param exportConfigurationProperties The properties
     * @return The new relic bean
     */
    @Singleton
    @Requires(missingProperty =  NEWRELIC_METRIC_API)
    NewRelicConfig newRelicConfig(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return exportConfig::getProperty;
    }

    /**
     * The new relic config bean.
     * @param exportConfigurationProperties The properties
     * @return The new relic bean
     */
    @Singleton
    @Requires(property =  NEWRELIC_METRIC_API)
    MicronautNewRelicRegistryConfig newRelicRegistryConfig(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return exportConfig::getProperty;
    }

    /**
     * Create a NewRelicRegistry bean if global metrics are enables, the metric endpoint is chosen
     * and the newrelic is enabled.
     *
     * @param config The the new relic config
     * @return A NewRelicRegistry
     */
    @Singleton
    @Requires(property =  NEWRELIC_METRIC_API)
    NewRelicRegistry newRelicMeterRegistry(MicronautNewRelicRegistryConfig config) throws UnknownHostException {
        NewRelicRegistry newRelicRegistry =
                NewRelicRegistry.builder(config)
                        .commonAttributes(
                                new Attributes()
                                        .put("host", InetAddress.getLocalHost().getHostName()))
                        .build();
        newRelicRegistry.config().meterFilter(MeterFilter.ignoreTags("plz_ignore_me"));
        newRelicRegistry.config().meterFilter(MeterFilter.denyNameStartsWith("jvm.threads"));
        return newRelicRegistry;
    }
}
