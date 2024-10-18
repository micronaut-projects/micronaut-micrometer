/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway;

import io.micronaut.configuration.metrics.micrometer.prometheus.PrometheusMeterRegistryFactory;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;

import java.util.Map;

/**
 * Configuration for the {@link PushGateway}.
 */
@ConfigurationProperties(PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_CONFIG)
@BootstrapContextCompatible
@Requires(property = PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(property = PrometheusMeterRegistryFactory.PROMETHEUS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Internal
final class PrometheusPushGatewayConfig {

    @ConfigurationBuilder(prefixes = "", excludes = {"basicAuth", "groupingKey"})
    final PushGateway.Builder builder = PushGateway.builder();

    private String basicAuthUsername;
    private String basicAuthPassword;

    private Map<String,String> groupingKeys;

    public Map<String, String> getGroupingKeys() {
        return groupingKeys;
    }

    public void setGroupingKeys(Map<String, String> groupingKeys) {
        this.groupingKeys = groupingKeys;
    }

    public String getBasicAuthUsername() {
        return basicAuthUsername;
    }

    public void setBasicAuthUsername(String basicAuthUsername) {
        this.basicAuthUsername = basicAuthUsername;
    }

    public String getBasicAuthPassword() {
        return basicAuthPassword;
    }

    public void setBasicAuthPassword(String basicAuthPassword) {
        this.basicAuthPassword = basicAuthPassword;
    }

}
