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
package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig;
import io.micronaut.configuration.metrics.binder.web.config.HttpMetricsConfig;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.filter.HttpClientFilter;
import jakarta.inject.Provider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * A {@link HttpClientFilter} that produces metrics under the key {@code http.client.requests}.
 *
 * <p>An around filter: the timer of the request is recorded exactly once, when the response or
 * the failure is received (the response of an error status is taken from the
 * {@link HttpResponseProvider} failure), or when the request is cancelled.</p>
 *
 * @author Denis Stepanov
 * @since 5.7
 */
@ClientFilter("${micronaut.metrics.http.client.path:/**}")
@RequiresMetrics
@Requires(property = HttpMetricsConfig.ENABLED, notEquals = FALSE)
@Requires(condition = WebMetricsClientCondition.class)
@Internal
final class ClientMetricsFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ClientMetricsFilter.class);
    private static final String EMBEDDED_SERVER = "embedded-server";

    // resolved lazily: the registry may depend on beans that use the HTTP client or server
    private final Supplier<MeterRegistry> meterRegistry;
    @Nullable
    private final Predicate<String> exclusions;

    /**
     * @param meterRegistryProvider the meter registry provider
     * @param exclusions            the regular expressions of the paths not to record
     */
    public ClientMetricsFilter(Provider<MeterRegistry> meterRegistryProvider,
                               @Property(name = "micronaut.metrics.http.client.exclusions") @Nullable List<String> exclusions) {
        this.meterRegistry = SupplierUtil.memoized(meterRegistryProvider::get);
        this.exclusions = HttpPathExclusions.of(exclusions);
    }

    /**
     * Times the request.
     *
     * @param request      The request
     * @param continuation The continuation
     * @return The response stage
     */
    @RequestFilter
    CompletionStage<HttpResponse<?>> filter(HttpRequest<?> request,
                                            FilterContinuation<CompletionStage<HttpResponse<?>>> continuation) {
        Predicate<String> pathExclusions = this.exclusions;
        if (pathExclusions != null && pathExclusions.test(request.getPath())) {
            return continuation.proceed();
        }
        return new RequestMetrics(this, request).recordOnCompletion(continuation.proceed());
    }

    /**
     * The stage returned by the filter: it records the timer exactly once, when the downstream
     * completes or when it is cancelled, and then completes with the outcome of the downstream.
     */
    private static final class RequestMetrics extends CompletableFuture<HttpResponse<?>>
        implements BiConsumer<HttpResponse<?>, Throwable> {

        private static final VarHandle RECORDED_HANDLE;

        static {
            try {
                RECORDED_HANDLE = MethodHandles.lookup().findVarHandle(RequestMetrics.class, "recorded", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final ClientMetricsFilter filter;
        private final HttpRequest<?> request;
        private final long start = System.nanoTime();
        @Nullable
        private CompletableFuture<HttpResponse<?>> downstream;
        @SuppressWarnings("unused") // accessed through RECORDED_HANDLE
        private volatile boolean recorded;

        RequestMetrics(ClientMetricsFilter filter, HttpRequest<?> request) {
            this.filter = filter;
            this.request = request;
        }

        CompletionStage<HttpResponse<?>> recordOnCompletion(CompletionStage<HttpResponse<?>> stage) {
            CompletableFuture<HttpResponse<?>> future = stage.toCompletableFuture();
            downstream = future;
            future.whenComplete(this);
            return this;
        }

        @Override
        public void accept(@Nullable HttpResponse<?> response, @Nullable Throwable error) {
            Throwable failure = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (failure == null) {
                recordOnce(response, null);
                complete(response);
            } else {
                recordOnce(failure instanceof HttpResponseProvider provider ? provider.getResponse() : null, failure);
                completeExceptionally(failure);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                recordOnce(null, new CancellationException());
                CompletableFuture<HttpResponse<?>> future = downstream;
                if (future != null) {
                    future.cancel(mayInterruptIfRunning);
                }
            }
            return cancelled;
        }

        @Nullable
        private static String resolvePath(HttpRequest<?> request) {
            // only include templated paths
            return BasicHttpAttributes.getUriTemplate(request).orElse(null);
        }

        private static String resolveServiceId(HttpRequest<?> request) {
            String serviceId = BasicHttpAttributes.getServiceId(request).orElse(null);
            if (serviceId != null && !serviceId.isEmpty()) {
                return serviceId.charAt(0) == '/' ? EMBEDDED_SERVER : serviceId;
            }
            return WebMetricsHelper.NO_SERVICE_ID;
        }

        private void recordOnce(@Nullable HttpResponse<?> response, @Nullable Throwable failure) {
            if (!RECORDED_HANDLE.compareAndSet(this, false, true)) {
                return;
            }
            try {
                WebMetricsHelper.recordTimer(
                    filter.meterRegistry.get(),
                    HttpClientMeterConfig.REQUESTS_METRIC,
                    start,
                    request.getMethodName(),
                    resolvePath(request),
                    resolveServiceId(request),
                    true,
                    response == null ? WebMetricsHelper.NO_STATUS : response.code(),
                    failure
                );
            } catch (RuntimeException e) {
                LOG.warn("Failed to record the {} timer: {}", HttpClientMeterConfig.REQUESTS_METRIC, e.getMessage(), e);
            }
        }
    }
}
