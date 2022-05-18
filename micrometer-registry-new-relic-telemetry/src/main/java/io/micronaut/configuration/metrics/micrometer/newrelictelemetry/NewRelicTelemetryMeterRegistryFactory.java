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
package io.micronaut.configuration.metrics.micrometer.newrelictelemetry;

import com.newrelic.telemetry.Attributes;
import com.newrelic.telemetry.micrometer.NewRelicRegistry;
import com.newrelic.telemetry.micrometer.NewRelicRegistryConfig;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a New Relic registry using the New Relic provided, telemetry SDK based Micrometer registry.
 *
 * @author mparlee
 */
@Factory
public class NewRelicTelemetryMeterRegistryFactory {

    public static final String NEWRELIC_CONFIG = MICRONAUT_METRICS_EXPORT + ".newrelic";
    public static final String NEWRELIC_ENABLED = NEWRELIC_CONFIG + ".enabled";

    /**
     * Create a NewRelicRegistry bean if global metrics are enabled
     * and New Relic is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param config the New Relic config
     * @return NewRelicRegistry
     */
    @Singleton
    NewRelicRegistry newRelicRegistry(NewRelicRegistryConfig config) throws UnknownHostException {
        NewRelicRegistry newRelicRegistry =
                NewRelicRegistry.builder(config)
                        .commonAttributes(
                                new Attributes()
                                        .put("host", InetAddress.getLocalHost().getHostName()))
                        .build();
        newRelicRegistry.config().meterFilter(MeterFilter.ignoreTags("plz_ignore_me"));
        newRelicRegistry.config().meterFilter(MeterFilter.denyNameStartsWith("jvm.threads"));
        newRelicRegistry.start(new NamedThreadFactory("newrelic.micrometer.registry"));
        return newRelicRegistry;
    }

    /**
     * The New Relic config bean.
     * @param exportConfigurationProperties the properties
     * @return the New Relic bean
     */
    @Singleton
    NewRelicTelemetryMicronautConfig newRelicRegistryConfig(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return exportConfig::getProperty;
    }
}
