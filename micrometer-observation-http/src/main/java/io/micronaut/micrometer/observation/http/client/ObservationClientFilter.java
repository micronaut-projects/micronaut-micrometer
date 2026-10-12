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
package io.micronaut.micrometer.observation.http.client;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.micrometer.observation.ObservationPropagationContext;
import io.micronaut.micrometer.observation.http.AbstractObservationFilter;
import io.micronaut.micrometer.observation.http.ObservationHttpExclusionsConfiguration;
import io.micronaut.micrometer.observation.http.client.instrumentation.ClientHttpObservationDocumentation;
import io.micronaut.micrometer.observation.http.client.instrumentation.ClientRequestObservationContext;
import io.micronaut.micrometer.observation.http.client.instrumentation.ClientRequestObservationConvention;
import io.micronaut.micrometer.observation.http.client.instrumentation.DefaultClientRequestObservationConvention;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.core.util.StringUtils.TRUE;
import static io.micronaut.micrometer.observation.http.AbstractObservationFilter.CLIENT_PATH;

/**
 * An HTTP client instrumentation filter that uses Micrometer Observation API.
 *
 * <p>An around filter: it starts the observation (which propagates it in the request headers),
 * makes it the current observation of the downstream filters and the request, and stops it exactly
 * once, when the response or the failure is received or when the request is cancelled. A failure
 * is the error of the observation, with the response of an {@link HttpResponseProvider} failure
 * (e.g. an error status), or without a response (e.g. the connection failed).</p>
 */
@Internal
@ClientFilter(CLIENT_PATH)
@Requires(property = "micrometer.observation.http.client.enabled", notEquals = FALSE, defaultValue = TRUE)
public final class ObservationClientFilter extends AbstractObservationFilter {

    private static final ClientRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultClientRequestObservationConvention();
    private static final Logger LOG = LoggerFactory.getLogger(ObservationClientFilter.class);

    private final ObservationRegistry observationRegistry;

    private final ClientRequestObservationConvention observationConvention;

    public ObservationClientFilter(
        ObservationRegistry observationRegistry,
        @Nullable ClientRequestObservationConvention observationConvention,
        @Nullable ObservationHttpExclusionsConfiguration exclusionsConfig
    ) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
    }

    /**
     * Observes the request.
     *
     * @param request      The request
     * @param continuation The continuation
     * @return The response stage, completed after the observation was stopped
     */
    @RequestFilter
    public CompletionStage<HttpResponse<?>> request(MutableHttpRequest<?> request,
                                                    FilterContinuation<CompletionStage<HttpResponse<?>>> continuation) {
        if (observationRegistry.isNoop() || shouldExclude(request.getPath())) {
            // nothing observes the requests (e.g. no handler), or excluded
            return continuation.proceed();
        }
        ClientRequestObservationContext observationContext = new ClientRequestObservationContext(request);
        Observation observation = ClientHttpObservationDocumentation.HTTP_CLIENT_EXCHANGES.observation(this.observationConvention,
            DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry).start();
        if (observation.isNoop()) {
            // e.g. disabled by an ObservationPredicate: there is nothing to stop
            return continuation.proceed();
        }
        request.setAttribute(MICROMETER_OBSERVATION_ATTRIBUTE_KEY, observation);

        // The observation is current for the downstream only: the propagated context of the filter is left
        // unchanged, and the returned stage completes with the context of the caller, so the code consuming
        // the response does not run with the client observation as its current observation.
        PropagatedContext callerContext = PropagatedContext.getOrEmpty();
        ClientObservation clientObservation = new ClientObservation(observation, observationContext, callerContext);
        CompletionStage<HttpResponse<?>> downstream;
        try {
            downstream = callerContext.plus(new ObservationPropagationContext(observation)).propagate(continuation::proceed);
        } catch (RuntimeException e) {
            clientObservation.accept(null, e);
            return clientObservation;
        }
        return clientObservation.stopOnCompletion(downstream);
    }

    /**
     * The stage returned by the filter: it stops the observation exactly once, when the downstream
     * completes or when it is cancelled, and only then completes with the outcome of the downstream,
     * in the context of the caller.
     */
    private static final class ClientObservation extends CompletableFuture<HttpResponse<?>>
        implements BiConsumer<HttpResponse<?>, Throwable> {

        private static final VarHandle STOPPED_HANDLE;

        static {
            try {
                STOPPED_HANDLE = MethodHandles.lookup().findVarHandle(ClientObservation.class, "stopped", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final Observation observation;
        private final ClientRequestObservationContext context;
        private final PropagatedContext completionContext;
        @Nullable
        private CompletableFuture<HttpResponse<?>> downstream;
        @SuppressWarnings("unused") // accessed through STOPPED_HANDLE
        private volatile boolean stopped;

        ClientObservation(Observation observation, ClientRequestObservationContext context, PropagatedContext completionContext) {
            this.observation = observation;
            this.context = context;
            this.completionContext = completionContext;
        }

        CompletionStage<HttpResponse<?>> stopOnCompletion(CompletionStage<HttpResponse<?>> stage) {
            CompletableFuture<HttpResponse<?>> future = stage.toCompletableFuture();
            downstream = future;
            future.whenComplete(this);
            return this;
        }

        @Override
        public void accept(@Nullable HttpResponse<?> response, @Nullable Throwable error) {
            Throwable failure = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (failure == null) {
                stop(response, null);
            } else if (failure instanceof CancellationException) {
                stop(null, null);
            } else {
                stop(failure instanceof HttpResponseProvider provider ? provider.getResponse() : null, failure);
            }
            if (completionContext.isBound()) {
                completeWith(response, failure);
            } else {
                completionContext.propagate(() -> completeWith(response, failure));
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                stop(null, null);
                CompletableFuture<HttpResponse<?>> future = downstream;
                if (future != null) {
                    future.cancel(mayInterruptIfRunning);
                }
            }
            return cancelled;
        }

        @Nullable
        private Object completeWith(@Nullable HttpResponse<?> response, @Nullable Throwable failure) {
            if (failure != null) {
                completeExceptionally(failure);
            } else {
                complete(response);
            }
            return null;
        }

        /**
         * Stops the observation, with the response if there is one, and the failure as its error.
         * Without a response the status is {@code CLIENT_ERROR}.
         *
         * @param response The response, or {@code null}
         * @param failure  The failure, or {@code null}
         */
        private void stop(@Nullable HttpResponse<?> response, @Nullable Throwable failure) {
            if (!STOPPED_HANDLE.compareAndSet(this, false, true)) {
                return;
            }
            try {
                if (failure != null) {
                    observation.error(failure);
                }
                if (response != null) {
                    context.setResponse(response);
                }
                observation.stop();
            } catch (RuntimeException e) {
                LOG.warn("Failed to stop the HTTP client observation: {}", e.getMessage(), e);
            }
        }
    }
}
