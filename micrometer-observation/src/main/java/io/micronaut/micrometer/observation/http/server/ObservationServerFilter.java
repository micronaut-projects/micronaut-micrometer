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
package io.micronaut.micrometer.observation.http.server;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.micrometer.observation.ObservationPropagationContext;
import io.micronaut.micrometer.observation.http.AbstractObservationFilter;
import io.micronaut.micrometer.observation.http.ObservationHttpExclusionsConfiguration;
import io.micronaut.micrometer.observation.http.server.instrumentation.DefaultServerRequestObservationConvention;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerHttpObservationDocumentation;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerRequestObservationContext;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerRequestObservationConvention;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.micrometer.observation.http.AbstractObservationFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Micrometer Observation API.
 */
@Internal
@Filter(SERVER_PATH)
@Requires(property = "micrometer.observation.http.server.enabled", notEquals = FALSE)
public final class ObservationServerFilter extends AbstractObservationFilter implements HttpServerFilter {

    private static final ServerRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultServerRequestObservationConvention();

    private final ObservationRegistry observationRegistry;

    private final ServerRequestObservationConvention observationConvention;

    public ObservationServerFilter(
        ObservationRegistry observationRegistry,
        @Nullable ServerRequestObservationConvention observationConvention,
        @Nullable ObservationHttpExclusionsConfiguration exclusionsConfig
    ) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

        if (shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        ServerRequestObservationContext context = new ServerRequestObservationContext(request);
        Observation observation = ServerHttpObservationDocumentation.HTTP_SERVER_REQUESTS.observation(this.observationConvention,
            DEFAULT_OBSERVATION_CONVENTION, () -> context, this.observationRegistry).start();

            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
                .plus(new ObservationPropagationContext(observation))
                .propagate()) {

                PropagatedContext propagatedContext = PropagatedContext.get();
                return Mono.from(chain.proceed(request))
                    .doOnNext(mutableHttpResponse -> mutableHttpResponse.getAttribute(HttpAttributes.EXCEPTION, Exception.class)
                        .ifPresentOrElse(
                            e -> onError(mutableHttpResponse, context, observation, e), () -> {
                                if (mutableHttpResponse.status().getCode() >= 400) {
                                    onError(mutableHttpResponse, context, observation, null);
                                } else {
                                    context.setResponse(mutableHttpResponse);
                                    observation.stop();
                                }
                            }))
                    .doOnError(throwable -> onError(null, context, observation, throwable))
                    .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext));
        }
    }

    private void onError(MutableHttpResponse<?> response, ServerRequestObservationContext context, Observation observation, @Nullable Throwable e) {
        if (response != null) {
            context.setResponse(response);
        }
        observation.error(e);
        observation.stop();
    }
}
