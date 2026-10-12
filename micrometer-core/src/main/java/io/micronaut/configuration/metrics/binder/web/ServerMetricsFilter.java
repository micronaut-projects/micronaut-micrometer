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
package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.configuration.metrics.binder.web.config.HttpMetricsConfig;
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.ErrorRouteInfo;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Provider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Registers the timers and meters for each request.
 *
 * <p>The default is to intercept all paths /**, but using the
 * property micronaut.metrics.http.path, this can be changed.</p>
 *
 * <p>The request filter starts the timer of the request, the response filter takes its tags from
 * the response, and the timer is recorded once the response is written, streamed bodies included:
 * the server releases the request then (see {@link LifecycleHttpRequest#addDisposalResource}). If
 * the response filters run again for the request, with the response that replaces one whose body
 * could not be written, the timer is recorded once, with that final response. A request without
 * a lifecycle, or a WebSocket upgrade, is recorded by the response filter.</p>
 *
 * @author Denis Stepanov
 * @since 5.7
 */
@ServerFilter("${micronaut.metrics.http.path:/**}")
@RequiresMetrics
@Requires(property = HttpMetricsConfig.ENABLED, notEquals = FALSE)
@Requires(condition = WebMetricsServerCondition.class)
@Internal
final class ServerMetricsFilter implements Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(ServerMetricsFilter.class);
    private static final String REQUEST_METRICS_ATTRIBUTE = ServerMetricsFilter.class.getName() + ".REQUEST_METRICS";
    private static final String UNMATCHED_URI = "UNMATCHED_URI";
    private static final String WEBSOCKET = "websocket";

    private final Provider<MeterRegistry> meterRegistryProvider;
    private final boolean reportClientErrorURIs;
    @Nullable
    private final Predicate<String> exclusions;
    @Nullable
    private volatile MeterRegistry meterRegistry;

    /**
     * @param meterRegistryProvider  the meter registry provider
     * @param clientErrorsUrisConfig the client errors
     * @param exclusions             the regular expressions of the paths not to record
     */
    public ServerMetricsFilter(Provider<MeterRegistry> meterRegistryProvider,
                               HttpMetricsConfig.ClientErrorsUrisConfig clientErrorsUrisConfig,
                               @Property(name = "micronaut.metrics.http.exclusions") @Nullable List<String> exclusions) {
        this.meterRegistryProvider = meterRegistryProvider;
        this.reportClientErrorURIs = clientErrorsUrisConfig.enabled();
        this.exclusions = HttpPathExclusions.of(exclusions);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.METRICS.order();
    }

    @RequestFilter
    void onRequest(HttpRequest<?> request) {
        Predicate<String> exclusions = this.exclusions;
        if (exclusions != null && exclusions.test(request.getPath())) {
            return;
        }
        if (request.getAttribute(REQUEST_METRICS_ATTRIBUTE).isPresent()) {
            // the filters run again for the same request: its timer runs already
            return;
        }
        RequestMetrics metrics = new RequestMetrics(this, request.getMethodName());
        request.setAttribute(REQUEST_METRICS_ATTRIBUTE, metrics);
        LifecycleHttpRequest<?> lifecycle = lifecycle(request);
        if (lifecycle != null) {
            metrics.deferred = true;
            lifecycle.addDisposalResource(metrics);
        }
    }

    @ResponseFilter
    void onResponse(HttpRequest<?> request, HttpResponse<?> response) {
        if (request.getAttribute(REQUEST_METRICS_ATTRIBUTE).orElse(null) instanceof RequestMetrics metrics) {
            metrics.responded(resolvePath(request), response);
        }
    }

    /**
     * The request that runs the recording once it is released, after its response was written.
     * A WebSocket upgrade request is not released when the connection is upgraded, so it is
     * recorded by the response filter.
     *
     * @param request The request
     * @return The lifecycle of the request, or {@code null}
     */
    @Nullable
    private static LifecycleHttpRequest<?> lifecycle(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (!(current instanceof LifecycleHttpRequest<?>)) {
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
        String upgrade = request.getHeaders().get(HttpHeaders.UPGRADE);
        if (upgrade != null && upgrade.equalsIgnoreCase(WEBSOCKET)) {
            return null;
        }
        return (LifecycleHttpRequest<?>) current;
    }

    private static String resolvePath(HttpRequest<?> request) {
        RouteMatch<?> routeMatch = RouteAttributes.getRouteMatch(request).orElse(null);
        if (routeMatch instanceof UriRouteMatch<?, ?> uriRouteMatch) {
            return uriRouteMatch.getRouteInfo().getUriMatchTemplate().toPathString();
        }
        return BasicHttpAttributes.getUriTemplate(request).orElse(UNMATCHED_URI);
    }

    private MeterRegistry meterRegistry() {
        MeterRegistry registry = meterRegistry;
        if (registry == null) {
            // resolved lazily, the registry may depend on beans that depend on the HTTP server
            registry = meterRegistryProvider.get();
            meterRegistry = registry;
        }
        return registry;
    }

    /**
     * The timer of a request: started by the request filter, tagged by the response filter, and
     * recorded exactly once, when the request is released after its response was written, or by
     * the response filter if the request has no lifecycle.
     */
    private static final class RequestMetrics implements Runnable, Supplier<Object> {

        private static final VarHandle RECORDED;

        static {
            try {
                RECORDED = MethodHandles.lookup().findVarHandle(RequestMetrics.class, "recorded", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final ServerMetricsFilter filter;
        private final long start = System.nanoTime();
        private final String method;
        /**
         * Whether the recording waits for the request to be released, set by the request filter
         * before it registers the recording.
         */
        private boolean deferred;
        // the tags of the last response, published by the volatile write of responded
        @Nullable
        private String path;
        private int status;
        @Nullable
        private Throwable exception;
        private PropagatedContext propagatedContext = PropagatedContext.empty();
        private volatile boolean responded;
        private volatile boolean released;
        @SuppressWarnings("unused") // accessed through RECORDED
        private volatile boolean recorded;

        RequestMetrics(ServerMetricsFilter filter, String method) {
            this.filter = filter;
            this.method = method;
        }

        /**
         * Takes the tags of the response. If the response filters run again for the request, the
         * tags of the response that replaces the first one are recorded.
         *
         * @param path     The path of the request
         * @param response The response
         */
        void responded(String path, HttpResponse<?> response) {
            int code = response.code();
            Throwable exception = RouteAttributes.getException(response).orElse(null);
            if (exception != null && code < 400
                && RouteAttributes.getRouteInfo(response).orElse(null) instanceof ErrorRouteInfo<?, ?>) {
                // an error route answered successfully: not an error. The response of an
                // exception handler keeps the exception, see HttpResponseProvider
                exception = null;
            }
            this.path = path;
            this.status = code;
            this.exception = exception;
            this.propagatedContext = PropagatedContext.getOrEmpty();
            responded = true;
            if (!deferred || released) {
                record();
            }
        }

        /**
         * The request was released, after its response was written or the connection closed.
         */
        @Override
        public void run() {
            released = true;
            if (!responded) {
                // no response yet: the response filter records the timer, if it runs
                return;
            }
            PropagatedContext context = propagatedContext;
            if (context.isEmpty()) {
                record();
            } else {
                // the meter filters see the context of the request, as in the response filter
                context.propagate((Supplier<Object>) this);
            }
        }

        @Override
        public Object get() {
            record();
            return this;
        }

        private void record() {
            if (!RECORDED.compareAndSet(this, false, true)) {
                return;
            }
            try {
                WebMetricsHelper.record(
                    filter.meterRegistry(),
                    HttpServerMeterConfig.REQUESTS_METRIC,
                    start,
                    method,
                    path,
                    null,
                    filter.reportClientErrorURIs,
                    status,
                    exception
                );
            } catch (RuntimeException e) {
                // the recording runs when the request is released, which must not fail
                LOG.warn("Failed to record the {} timer: {}", HttpServerMeterConfig.REQUESTS_METRIC, e.getMessage(), e);
            }
        }
    }
}
