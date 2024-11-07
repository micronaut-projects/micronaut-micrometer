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
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Provider;

import java.util.Optional;
import java.util.function.Supplier;

import static io.micronaut.core.util.StringUtils.TRUE;

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
@Requires(bean = HttpMetricsConfig.class, beanProperty = "enabled", value = TRUE)
@Requires(condition = WebMetricsServerCondition.class)
@Internal
final class ServerMetricsFilter {

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

    private String resolvePath(HttpRequest<?> request) {
        Optional<String> routeInfo = request.getAttribute(HttpAttributes.ROUTE_INFO, UriRouteMatch.class)
            .map(match -> match.getRouteInfo().getUriMatchTemplate().toPathString());
        return routeInfo.orElseGet(() -> request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class)
            .orElse(UNMATCHED_URI));
    }

    @RequestFilter
    void onRequest(HttpRequest<?> request) {
        request.setAttribute(START_ATTRIBUTE, System.nanoTime());
    }

    @ResponseFilter
    void onResponse(HttpRequest<?> request, HttpResponse<?> response) {
        WebMetricsHelper httpResponseWebMetricsPublisher = new WebMetricsHelper(
            meterRegistryProvider.get(),
            resolvePath(request),
            request.getAttribute(START_ATTRIBUTE, Long.class).orElseGet(System::nanoTime),
            request.getMethod().toString(),
            HttpServerMeterConfig.REQUESTS_METRIC,
            null,
            reportClientErrorURIs
        );
        httpResponseWebMetricsPublisher.onResponse(response);
    }
}
