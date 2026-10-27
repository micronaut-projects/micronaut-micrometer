/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway.PrometheusPushGatewayConfig.ShutdownOperation;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Internal;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

/**
 * Pushes or deletes the metrics in the Pushgateway when the application shuts down, as configured with
 * {@code micronaut.metrics.export.prometheus.pushgateway.shutdown-operation}.
 *
 * @since 6.2.0
 */
@Singleton
@Requires(beans = PushGateway.class)
@Requires(property = PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_SHUTDOWN_OPERATION)
@Internal
final class PrometheusPushGatewayShutdownListener implements ApplicationEventListener<ShutdownEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusPushGatewayShutdownListener.class);

    private final PushGateway pushGateway;
    private final ShutdownOperation shutdownOperation;

    PrometheusPushGatewayShutdownListener(PushGateway pushGateway, PrometheusPushGatewayConfig config) {
        this.pushGateway = pushGateway;
        this.shutdownOperation = config.getShutdownOperation();
    }

    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        try {
            switch (shutdownOperation) {
                case PUSH -> pushGateway.push();
                case DELETE -> pushGateway.delete();
                default -> {
                    // the metrics are left in the Pushgateway
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to {} the metrics in the Prometheus Pushgateway on shutdown",
                shutdownOperation.name().toLowerCase(Locale.ROOT), e);
        }
    }
}
