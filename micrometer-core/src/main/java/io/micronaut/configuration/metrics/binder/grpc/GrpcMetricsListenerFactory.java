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
public class GrpcMetricsListenerFactory {

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
