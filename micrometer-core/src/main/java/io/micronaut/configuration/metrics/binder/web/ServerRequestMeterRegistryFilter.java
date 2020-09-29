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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * Once per request web filter that will register the timers
 * and meters for each request.
 *
 * <p>The default is to intercept all paths /**, but using the
 *  property micronaut.metrics.http.path, this can be changed.</p>
 *
 *
 * @author Christian Oestreich
 * @author graemerocher
 * @since 1.0
 */
@Filter("${micronaut.metrics.http.path:/**}")
@RequiresMetrics
@Requires(property = WebMetricsPublisher.ENABLED, notEquals = StringUtils.FALSE)
public class ServerRequestMeterRegistryFilter implements HttpServerFilter {

    private final MeterRegistry meterRegistry;

    /**
     * Filter constructor.
     *
     * @param meterRegistry the meter registry
     */
    public ServerRequestMeterRegistryFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private String resolvePath(HttpRequest<?> request) {
        Optional<String> route = request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
        return route.orElseGet(request::getPath);
    }

    /**
     * This method is here for backwards compatibility. The class no longer implement
     * OncePerRequestHttpServerFilter, however the method was kept to maintain binary
     * compatibility.
     *
     * @param request The request
     * @param chain The filter chain
     * @return A response publisher
     * @deprecated Override {@link #doFilter(HttpRequest, ServerFilterChain)} instead.
     */
    @Deprecated
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        long start = System.nanoTime();
        Publisher<MutableHttpResponse<?>> responsePublisher = chain.proceed(request);
        String path = resolvePath(request);
        String attributeKey = "micronaut.filter." + getClass().getSimpleName();
        Optional<Boolean> attribute = request.getAttribute(attributeKey, Boolean.class);
        boolean reportErrors = attribute.isPresent();
        if (!attribute.isPresent()) {
            request.setAttribute(attributeKey, true);
        }
        return new WebMetricsPublisher<>(
                responsePublisher,
                meterRegistry,
                path,
                start,
                request.getMethod().toString(),
                reportErrors
        );
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return doFilterOnce(request, chain);
    }
}
