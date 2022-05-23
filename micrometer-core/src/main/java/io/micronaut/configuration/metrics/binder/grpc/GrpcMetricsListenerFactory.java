/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.configuration.metrics.binder.grpc;

import io.grpc.ClientInterceptor;
import io.grpc.Internal;
import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingServerInterceptor;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Provides interceptor beans to collect metrics.
 *
 * @author James Kleeh
 * @since 4.1.0
 */
@Internal
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".grpc.enabled", notEquals = FALSE)
@Factory
class GrpcMetricsListenerFactory {

    @Singleton
    @Requires(classes = ServerInterceptor.class)
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".grpc.server.enabled", notEquals = FALSE)
    MetricCollectingServerInterceptor grpcServerMetrics(MeterRegistry meterRegistry) {
        return new MetricCollectingServerInterceptor(meterRegistry);
    }

    @Singleton
    @Requires(classes = ClientInterceptor.class)
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".grpc.client.enabled", notEquals = FALSE)
    MetricCollectingClientInterceptor grpcClientMetrics(MeterRegistry meterRegistry) {
        return new MetricCollectingClientInterceptor(meterRegistry);
    }
}
