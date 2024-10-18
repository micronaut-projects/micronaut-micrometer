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
package io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a Prometheus {@link PushGateway}.
 */
@Factory
@Internal
class PrometheusPushGatewayFactory {

    public static final String PROMETHEUS_PUSHGATEWAY_CONFIG = MICRONAUT_METRICS_EXPORT + ".prometheus.pushgateway";
    public static final String PROMETHEUS_PUSHGATEWAY_ENABLED = PROMETHEUS_PUSHGATEWAY_CONFIG + ".enabled";

    /**
     * Create a PushGateway bean if global metrics are enabled
     * , Prometheus is enabled and PushGateway is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @return PushGateway
     */
    @Singleton
    @Requires(beans = PrometheusPushGatewayConfig.class)
    @Requires(beans = PrometheusMeterRegistry.class)
    PushGateway pushGateway(PrometheusMeterRegistry prometheusMeterRegistry, PrometheusPushGatewayConfig prometheusRegistryConfig) {
        PushGateway.Builder builder =  prometheusRegistryConfig.builder.registry(prometheusMeterRegistry.getPrometheusRegistry());

        if (!StringUtils.isEmpty(prometheusRegistryConfig.getBasicAuthUsername()) && !StringUtils.isEmpty(prometheusRegistryConfig.getBasicAuthPassword())) {
            builder.basicAuth(prometheusRegistryConfig.getBasicAuthUsername(), prometheusRegistryConfig.getBasicAuthPassword());
        }

        if (prometheusRegistryConfig.getGroupingKeys() != null && !prometheusRegistryConfig.getGroupingKeys().isEmpty()) {
            prometheusRegistryConfig.getGroupingKeys().forEach(builder::groupingKey);
        }

        return builder.build();
    }

}
