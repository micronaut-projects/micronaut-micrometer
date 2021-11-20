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
import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingServerInterceptor;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Provides interceptor beans to collect metrics.
 *
 * @author James Kleeh
 * @since 4.1.0
 */
@Factory
class GrpcMetricsListenerFactory {

    @Singleton
    @Requires(classes = ServerInterceptor.class)
    MetricCollectingServerInterceptor grpcServerMetrics(MeterRegistry meterRegistry) {
        return new MetricCollectingServerInterceptor(meterRegistry);
    }

    @Singleton
    @Requires(classes = ClientInterceptor.class)
    MetricCollectingClientInterceptor grpcClientMetrics(MeterRegistry meterRegistry) {
        return new MetricCollectingClientInterceptor(meterRegistry);
    }
}
