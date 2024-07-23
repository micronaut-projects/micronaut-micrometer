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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import jakarta.inject.Provider;
import org.reactivestreams.Publisher;

import java.util.Optional;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

/**
 * A {@link HttpClientFilter} that produces metrics under the key {@code http.client.requests}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter("${micronaut.metrics.http.client.path:/**}")
@RequiresMetrics
@Requires(property = WebMetricsPublisher.ENABLED, notEquals = FALSE)
@Requires(condition = WebMetricsClientCondition.class)
public class ClientRequestMetricRegistryFilter implements HttpClientFilter {

    private final Provider<MeterRegistry> meterRegistryProvider;

    /**
     * @param meterRegistryProvider the meter registry provider
     */
    public ClientRequestMetricRegistryFilter(Provider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        long start = System.nanoTime();
        Publisher<? extends HttpResponse<?>> responsePublisher = chain.proceed(request);

        return new WebMetricsPublisher<>(
                responsePublisher,
                meterRegistryProvider.get(),
                resolvePath(request),
                start,
                request.getMethod().toString(),
                false,
                resolveServiceID(request),
                true,
                true
        );
    }

    private String resolvePath(MutableHttpRequest<?> request) {
        Optional<String> route = request.getAttribute(URI_TEMPLATE, String.class);
        // only include templated paths
        return route.orElse(null);
    }

    @SuppressWarnings("java:S2259") // false positive
    private String resolveServiceID(MutableHttpRequest<?> request) {
        String serviceId = request.getAttributes().get(HttpAttributes.SERVICE_ID.toString(), String.class).orElse(null);
        if (StringUtils.isNotEmpty(serviceId) && serviceId.charAt(0) == '/') {
            return "embedded-server";
        }
        return serviceId;
    }
}
