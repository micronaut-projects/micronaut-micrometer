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
import io.micronaut.context.annotation.Value;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Provider;
import org.reactivestreams.Publisher;
import java.util.Optional;

/**
 * Registers the timers and meters for each request.
 *
 * <p>The default is to intercept all paths /**, but using the
 * property micronaut.metrics.http.path, this can be changed.</p>
 *
 * @author Christian Oestreich
 * @author graemerocher
 * @since 1.0
 * @deprecated Internal use only, replaced by a new implementation
 */
@Deprecated(forRemoval = true, since = "5.9")
public class ServerRequestMeterRegistryFilter implements HttpServerFilter {

    private static final String ATTRIBUTE_KEY = "micronaut.filter." + ServerRequestMeterRegistryFilter.class.getSimpleName();
    private static final String UNMATCHED_URI = "UNMATCHED_URI";
    private final Provider<MeterRegistry> meterRegistryProvider;

    @Value("${" + WebMetricsPublisher.CLIENT_ERROR_URIS_ENABLED + ":true}")
    private boolean reportClientErrorURIs;

    /**
     * @param meterRegistryProvider the meter registry provider
     */
    public ServerRequestMeterRegistryFilter(Provider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    private String resolvePath(HttpRequest<?> request) {
        Optional<String> routeInfo = RouteAttributes.getRouteMatch(request)
            .filter(UriRouteMatch.class::isInstance)
            .map(UriRouteMatch.class::cast)
            .map(match -> match.getRouteInfo().getUriMatchTemplate().toPathString());
        return routeInfo.orElseGet(() -> BasicHttpAttributes.getUriTemplate(request)
                        .orElse(UNMATCHED_URI));
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        long start = System.nanoTime();
        Publisher<MutableHttpResponse<?>> responsePublisher = chain.proceed(request);
        String path = resolvePath(request);
        Optional<Boolean> attribute = request.getAttribute(ATTRIBUTE_KEY, Boolean.class);
        boolean reportErrors = attribute.isPresent();
        if (attribute.isEmpty()) {
            request.setAttribute(ATTRIBUTE_KEY, true);
        }
        return new WebMetricsPublisher<>(
            responsePublisher,
            meterRegistryProvider.get(),
            path,
            start,
            request.getMethodName(),
            reportErrors,
            reportClientErrorURIs
        );
    }
}
