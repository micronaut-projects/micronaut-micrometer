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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Provider;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Registers the timers and meters for each request.
 *
 * <p>The default is to intercept all paths /**, but using the
 * property micronaut.metrics.http.path, this can be changed.</p>
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

    private static final String START_ATTRIBUTE = ServerMetricsFilter.class.getName() + ".START_ATTRIBUTE";
    private static final String UNMATCHED_URI = "UNMATCHED_URI";
    private final Supplier<MeterRegistry> meterRegistryProvider;

    private final boolean reportClientErrorURIs;

    /**
     * @param meterRegistryProvider  the meter registry provider
     * @param clientErrorsUrisConfig the client errors
     */
    public ServerMetricsFilter(Provider<MeterRegistry> meterRegistryProvider, HttpMetricsConfig.ClientErrorsUrisConfig clientErrorsUrisConfig) {
        this.meterRegistryProvider = SupplierUtil.memoized(meterRegistryProvider::get);
        this.reportClientErrorURIs = clientErrorsUrisConfig.enabled();
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.METRICS.order();
    }

    private String resolvePath(HttpRequest<?> request) {
        Optional<String> routeInfo = RouteAttributes.getRouteMatch(request)
            .filter(UriRouteMatch.class::isInstance)
            .map(UriRouteMatch.class::cast)
            .map(match -> match.getRouteInfo().getUriMatchTemplate().toPathString());
        return routeInfo.orElseGet(() -> BasicHttpAttributes.getUriTemplate(request)
            .orElse(UNMATCHED_URI));
    }

    @RequestFilter
    void onRequest(HttpRequest<?> request) {
        request.setAttribute(START_ATTRIBUTE, System.nanoTime());
    }

    @ResponseFilter
    void onResponse(HttpRequest<?> request, HttpResponse<?> response) {
        WebMetricsHelper webMetricsHelper = new WebMetricsHelper(
            meterRegistryProvider.get(),
            resolvePath(request),
            request.getAttribute(START_ATTRIBUTE, Long.class).orElseGet(System::nanoTime),
            request.getMethod().toString(),
            HttpServerMeterConfig.REQUESTS_METRIC,
            null,
            reportClientErrorURIs
        );
        Object body = response.body();
        if (response instanceof io.micronaut.http.MutableHttpResponse<?> mutableHttpResponse && body instanceof Publisher<?> publisher) {
            mutableHttpResponse.body(Flux.from(publisher)
                .doOnError(throwable -> webMetricsHelper.error(response, throwable))
                .doFinally(signalType -> {
                    if (signalType != SignalType.ON_ERROR) {
                        webMetricsHelper.onResponse(response);
                    }
                }));
            return;
        }
        if (response instanceof io.micronaut.http.MutableHttpResponse<?> mutableHttpResponse && body instanceof CompletionStage<?> completionStage) {
            mutableHttpResponse.body(completionStage.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    webMetricsHelper.onResponse(response);
                } else {
                    webMetricsHelper.error(response, throwable);
                }
            }));
            return;
        }
        webMetricsHelper.onResponse(response);
    }
}
