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
package io.micronaut.micrometer.observation.grpc;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.binder.grpc.GrpcClientObservationConvention;
import io.micrometer.core.instrument.binder.grpc.GrpcServerObservationConvention;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Creates the Micrometer observation interceptors for gRPC servers and clients.
 *
 * <p>Micronaut gRPC applies every {@link ServerInterceptor} bean to the gRPC server and every
 * {@link ClientInterceptor} bean to the managed channels it creates. A
 * {@link GrpcServerObservationConvention} or {@link GrpcClientObservationConvention} bean replaces
 * the default convention.</p>
 *
 * <p>When an interceptor is enabled, the corresponding {@code MetricCollecting*Interceptor} of
 * {@code micronaut-micrometer-core} is not created, so that calls are not measured twice.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Factory
public class ObservationGrpcInterceptorFactory {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "micrometer.observation.grpc";

    /**
     * Whether gRPC server observations are enabled.
     */
    public static final String SERVER_ENABLED = PREFIX + ".server.enabled";

    /**
     * Whether gRPC client observations are enabled.
     */
    public static final String CLIENT_ENABLED = PREFIX + ".client.enabled";

    /**
     * Creates the gRPC server observation interceptor.
     *
     * @param observationRegistry The observation registry
     * @param convention          The custom convention, if any
     * @return The interceptor
     */
    @Singleton
    @Requires(classes = ServerInterceptor.class)
    @Requires(property = SERVER_ENABLED, notEquals = FALSE)
    public ObservationGrpcServerInterceptor observationGrpcServerInterceptor(ObservationRegistry observationRegistry,
                                                                             @Nullable GrpcServerObservationConvention convention) {
        var interceptor = new ObservationGrpcServerInterceptor(observationRegistry);
        if (convention != null) {
            interceptor.setCustomConvention(convention);
        }
        return interceptor;
    }

    /**
     * Creates the gRPC client observation interceptor.
     *
     * @param observationRegistry The observation registry
     * @param convention          The custom convention, if any
     * @return The interceptor
     */
    @Singleton
    @Requires(classes = ClientInterceptor.class)
    @Requires(property = CLIENT_ENABLED, notEquals = FALSE)
    public ObservationGrpcClientInterceptor observationGrpcClientInterceptor(ObservationRegistry observationRegistry,
                                                                             @Nullable GrpcClientObservationConvention convention) {
        var interceptor = new ObservationGrpcClientInterceptor(observationRegistry);
        if (convention != null) {
            interceptor.setCustomConvention(convention);
        }
        return interceptor;
    }
}
