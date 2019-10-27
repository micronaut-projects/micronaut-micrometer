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
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

import java.util.Optional;

import static io.micronaut.configuration.metrics.binder.web.WebMetricsPublisher.USE_HISTOGRAM;

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
public class ServerRequestMeterRegistryFilter extends OncePerRequestHttpServerFilter {

    private final MeterRegistry meterRegistry;

    private final String USE_HISTOGRAM_VALUE_STRING = "${" + USE_HISTOGRAM + ":false}";
    @Value(USE_HISTOGRAM_VALUE_STRING)
    private boolean useHistogram;


    /**
     * Filter constructor.
     *
     * @param meterRegistry the meter registry
     */
    public ServerRequestMeterRegistryFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * The method that will be invoked once per request.
     *
     * @param httpRequest the http request
     * @param chain       The {@link ServerFilterChain} instance
     * @return a publisher with the response
     */
    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> httpRequest, ServerFilterChain chain) {
        long start = System.nanoTime();
        Publisher<MutableHttpResponse<?>> responsePublisher = chain.proceed(httpRequest);
        String path = resolvePath(httpRequest);
        return new WebMetricsPublisher<>(
                responsePublisher,
                meterRegistry,
                path,
                start,
                httpRequest.getMethod().toString(),
                true,
                useHistogram
        );
    }

    private String resolvePath(HttpRequest<?> request) {
        Optional<String> route = request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
        return route.orElseGet(request::getPath);
    }

}
