/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.micrometer.observation.http.server;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.micrometer.observation.ObservationPropagationContext;
import io.micronaut.micrometer.observation.http.AbstractObservationFilter;
import io.micronaut.micrometer.observation.http.ObservationHttpExclusionsConfiguration;
import io.micronaut.micrometer.observation.http.server.instrumentation.DefaultServerRequestObservationConvention;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerHttpObservationDocumentation;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerRequestObservationContext;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerRequestObservationConvention;
import io.micronaut.web.router.RouteAttributes;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.core.util.StringUtils.TRUE;
import static io.micronaut.micrometer.observation.http.AbstractObservationFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Micrometer Observation API.
 *
 * <p>The request filter starts the observation and makes it the propagated context of the request,
 * the response filter sets the response, and the observation is stopped once the response is
 * written, streamed bodies included: the server releases the request then (see
 * {@link LifecycleHttpRequest#addDisposalResource}). If the response filters run again for the
 * request, with the response that replaces one whose body could not be written, the observation
 * is stopped once, with that final response. A request released without a response stops the
 * observation without one. A request without a lifecycle, or a WebSocket upgrade, is stopped by
 * the response filter.</p>
 *
 * <p>The filter runs in the {@link ServerFilterPhase#METRICS METRICS} phase, like the web metrics
 * filter, so before the tracing, session and security filters.</p>
 */
@Internal
@ServerFilter(SERVER_PATH)
@Requires(property = "micrometer.observation.http.server.enabled", notEquals = FALSE, defaultValue = TRUE)
public final class ObservationServerFilter extends AbstractObservationFilter implements Ordered {
    private static final ServerRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultServerRequestObservationConvention();
    private static final Logger LOG = LoggerFactory.getLogger(ObservationServerFilter.class);
    private static final String SERVER_OBSERVATION_ATTRIBUTE = ObservationServerFilter.class.getName() + ".SERVER_OBSERVATION";

    private final ObservationRegistry observationRegistry;

    private final ServerRequestObservationConvention observationConvention;

    public ObservationServerFilter(
        ObservationRegistry observationRegistry,
        @Nullable ServerRequestObservationConvention observationConvention,
        @Nullable ObservationHttpExclusionsConfiguration exclusionsConfig
    ) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.METRICS.order();
    }

    @RequestFilter
    public void request(HttpRequest<?> request, MutablePropagatedContext mutablePropagatedContext) {
        if (shouldExclude(request.getPath()) || request.getAttribute(SERVER_OBSERVATION_ATTRIBUTE).isPresent()) {
            // excluded, or the filters run again for the same request
            return;
        }
        ServerRequestObservationContext context = new ServerRequestObservationContext(request);
        Observation observation = ServerHttpObservationDocumentation.HTTP_SERVER_REQUESTS.observation(this.observationConvention,
            DEFAULT_OBSERVATION_CONVENTION, () -> context, this.observationRegistry).start();
        ServerObservation serverObservation = new ServerObservation(observation, context);
        request.setAttribute(MICROMETER_OBSERVATION_ATTRIBUTE_KEY, observation);
        request.setAttribute(SERVER_OBSERVATION_ATTRIBUTE, serverObservation);
        mutablePropagatedContext.add(new ObservationPropagationContext(observation));
        LifecycleHttpRequest<?> lifecycle = lifecycle(request);
        if (lifecycle != null) {
            serverObservation.deferred = true;
            lifecycle.addDisposalResource(serverObservation);
        }
    }

    @ResponseFilter
    public void response(HttpRequest<?> request, MutableHttpResponse<?> response) {
        if (request.getAttribute(SERVER_OBSERVATION_ATTRIBUTE).orElse(null) instanceof ServerObservation serverObservation) {
            serverObservation.responded(response);
        }
    }

    /**
     * The observation of a request, stopped exactly once: when the request is released after its
     * response was written, or by the response filter if the request has no lifecycle.
     */
    private static final class ServerObservation implements Runnable, Supplier<Object> {

        private static final VarHandle STOPPED_HANDLE;

        static {
            try {
                STOPPED_HANDLE = MethodHandles.lookup().findVarHandle(ServerObservation.class, "stopped", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final Observation observation;
        private final ServerRequestObservationContext context;
        /**
         * Whether the observation is stopped when the request is released, set by the request
         * filter before it registers the stop.
         */
        private boolean deferred;
        // published by the volatile write of responded
        private PropagatedContext propagatedContext = PropagatedContext.empty();
        private volatile boolean responded;
        private volatile boolean released;
        @SuppressWarnings("unused") // accessed through STOPPED_HANDLE
        private volatile boolean stopped;

        ServerObservation(Observation observation, ServerRequestObservationContext context) {
            this.observation = observation;
            this.context = context;
        }

        /**
         * Sets the response. If the response filters run again for the request, the response that
         * replaces the first one is the response of the observation.
         *
         * @param response The response
         */
        void responded(MutableHttpResponse<?> response) {
            if (stopped) {
                return;
            }
            Throwable exception = RouteAttributes.getException(response).orElse(null);
            if (exception != null) {
                observation.error(exception);
            }
            context.setResponse(response);
            propagatedContext = PropagatedContext.getOrEmpty();
            responded = true;
            if (!deferred || released) {
                stop();
            }
        }

        /**
         * The request was released, after its response was written or the connection closed.
         */
        @Override
        public void run() {
            released = true;
            PropagatedContext requestContext = propagatedContext;
            if (!responded || requestContext.isEmpty()) {
                stop();
            } else {
                // the handlers see the context of the request, as in the response filter
                requestContext.propagate((Supplier<Object>) this);
            }
        }

        @Override
        public Object get() {
            stop();
            return this;
        }

        private void stop() {
            if (!STOPPED_HANDLE.compareAndSet(this, false, true)) {
                return;
            }
            try {
                observation.stop();
            } catch (RuntimeException e) {
                // the observation is stopped when the request is released, which must not fail
                LOG.warn("Failed to stop the HTTP server observation: {}", e.getMessage(), e);
            }
        }
    }
}
