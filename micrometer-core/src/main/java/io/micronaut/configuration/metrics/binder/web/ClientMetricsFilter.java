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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.filter.HttpClientFilter;
import jakarta.inject.Provider;

import java.util.Optional;

import static io.micronaut.core.util.StringUtils.TRUE;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

/**
 * A {@link HttpClientFilter} that produces metrics under the key {@code http.client.requests}.
 *
 * @author Denis Stepanov
 * @since 5.7
 */
@ClientFilter("${micronaut.metrics.http.client.path:/**}")
@RequiresMetrics
@Requires(bean = HttpMetricsConfig.class, beanProperty = "enabled", value = TRUE)
@Requires(condition = WebMetricsClientCondition.class)
@Internal
final class ClientMetricsFilter {

    private static final String START_ATTRIBUTE = ClientMetricsFilter.class.getName() + ".START_ATTRIBUTE";
    private final Provider<MeterRegistry> meterRegistryProvider;

    /**
     * @param meterRegistryProvider the meter registry provider
     */
    public ClientMetricsFilter(Provider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @RequestFilter
    void onRequest(HttpRequest<?> request) {
        request.setAttribute(START_ATTRIBUTE, System.nanoTime());
    }

    @ResponseFilter
    void onResponse(HttpRequest<?> request, HttpResponse<?> response) {
        createHelper(request).onResponse(response);
    }

    @ResponseFilter
    void doException(HttpRequest<?> request, Throwable throwable) {
        createHelper(request).error(throwable);
    }

    private WebMetricsHelper createHelper(HttpRequest<?> request) {
        return new WebMetricsHelper(
            meterRegistryProvider.get(),
            resolvePath(request),
            request.getAttribute(START_ATTRIBUTE, Long.class).orElseGet(System::nanoTime),
            request.getMethod().toString(),
            HttpClientMeterConfig.REQUESTS_METRIC,
            resolveServiceID(request),
            true
        );
    }

    private String resolvePath(HttpRequest<?> request) {
        Optional<String> route = request.getAttribute(URI_TEMPLATE, String.class);
        // only include templated paths
        return route.orElse(null);
    }

    @SuppressWarnings("java:S2259") // false positive
    private String resolveServiceID(HttpRequest<?> request) {
        String serviceId = request.getAttributes().get(HttpAttributes.SERVICE_ID.toString(), String.class).orElse(null);
        if (StringUtils.isNotEmpty(serviceId) && serviceId.charAt(0) == '/') {
            return "embedded-server";
        }
        return serviceId;
    }
}
