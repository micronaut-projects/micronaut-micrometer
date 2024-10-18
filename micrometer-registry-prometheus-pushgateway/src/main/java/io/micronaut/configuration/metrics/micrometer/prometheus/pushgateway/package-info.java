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
/**
 * Micronaut integration with Prometheus PushGateway.
 */
@Configuration
@Requires(property = PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_ENABLED, notEquals = StringUtils.FALSE)
@Requires(property = PrometheusMeterRegistryFactory.PROMETHEUS_ENABLED, notEquals = StringUtils.FALSE)
package io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway;

import io.micronaut.configuration.metrics.micrometer.prometheus.PrometheusMeterRegistryFactory;
import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
