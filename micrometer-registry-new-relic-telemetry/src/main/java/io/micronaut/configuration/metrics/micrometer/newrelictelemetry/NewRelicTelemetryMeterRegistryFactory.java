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
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;
import java.util.function.UnaryOperator;

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

    private static final Logger LOG = LoggerFactory.getLogger(NewRelicTelemetryMeterRegistryFactory.class);
    private static final String HOST_ATTRIBUTE = "host";

    /**
     * Create a NewRelicRegistry bean if global metrics are enabled
     * and New Relic is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param config the New Relic config
     * @return NewRelicRegistry
     */
    @Singleton
    NewRelicRegistry newRelicRegistry(NewRelicRegistryConfig config) {
        NewRelicRegistry newRelicRegistry =
                NewRelicRegistry.builder(config)
                        .commonAttributes(commonAttributes(() -> InetAddress.getLocalHost().getHostName(), System::getenv))
                        .build();
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

    /**
     * Resolves the common attributes. The {@code host} attribute is the local host name, or the {@code HOSTNAME} or
     * {@code COMPUTERNAME} environment variable when the local host name cannot be resolved, for example in a
     * container without a resolvable host name. It is omitted when none of these are available.
     *
     * @param hostNameLookup The local host name lookup
     * @param environment The environment variables
     * @return The common attributes
     */
    static Attributes commonAttributes(HostNameLookup hostNameLookup, UnaryOperator<String> environment) {
        Attributes attributes = new Attributes();
        String host;
        try {
            host = hostNameLookup.hostName();
        } catch (IOException e) {
            host = environment.apply("HOSTNAME");
            if (StringUtils.isEmpty(host)) {
                host = environment.apply("COMPUTERNAME");
            }
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unable to resolve the local host name for the New Relic '{}' attribute, using: {}", HOST_ATTRIBUTE, host, e);
            }
        }
        if (StringUtils.isNotEmpty(host)) {
            attributes.put(HOST_ATTRIBUTE, host);
        }
        return attributes;
    }

    /**
     * Looks up the local host name.
     */
    @FunctionalInterface
    interface HostNameLookup {
        /**
         * @return The host name
         * @throws IOException if the host name cannot be resolved
         */
        String hostName() throws IOException;
    }
}
