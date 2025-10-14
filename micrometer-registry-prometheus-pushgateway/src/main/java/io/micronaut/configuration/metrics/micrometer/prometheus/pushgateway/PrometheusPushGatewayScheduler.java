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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.annotation.Scheduled;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * PrometheusPushGatewayScheduler pushes data to Prometheus pushGateway.
 */
@Singleton
@Requires(beans = PushGateway.class)
@Internal
final class PrometheusPushGatewayScheduler {

    private final PushGateway pushGateway;

    PrometheusPushGatewayScheduler(PushGateway pushGateway) {
        this.pushGateway = pushGateway;
    }

    @Scheduled(
        fixedDelay = "${micronaut.metrics.export.prometheus.pushgateway.interval:1m}",
        initialDelay = "${micronaut.metrics.export.prometheus.pushgateway.initial-delay:1m}")
    void pushData() throws IOException {
        pushGateway.push();
    }

}
