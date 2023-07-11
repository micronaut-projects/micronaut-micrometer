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
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.micrometer.observation.ObservationPropagationContext;
import io.micronaut.micrometer.observation.http.AbstractObservationFilter;
import io.micronaut.micrometer.observation.http.ObservationHttpExclusionsConfiguration;
import io.micronaut.micrometer.observation.http.server.instrumentation.DefaultServerRequestObservationConvention;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerHttpObservationDocumentation;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerRequestObservationContext;
import io.micronaut.micrometer.observation.http.server.instrumentation.ServerRequestObservationConvention;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.http.HttpAttributes.EXCEPTION;
import static io.micronaut.micrometer.observation.http.AbstractObservationFilter.SERVER_PATH;

/**
 * An HTTP server instrumentation filter that uses Micrometer Observation API.
 */
@Internal
@ServerFilter(SERVER_PATH)
@Requires(property = "micrometer.observation.http.server.enabled", notEquals = FALSE)
public final class ObservationServerFilter extends AbstractObservationFilter {
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

    @RequestFilter
    public void request(HttpRequest<?> request, MutablePropagatedContext mutablePropagatedContext) {
        if (shouldExclude(request.getPath())) {
            return;
        }
        ServerRequestObservationContext context = new ServerRequestObservationContext(request);
        Observation observation = ServerHttpObservationDocumentation.HTTP_SERVER_REQUESTS.observation(this.observationConvention,
            DEFAULT_OBSERVATION_CONVENTION, () -> context, this.observationRegistry).start();
        request.setAttribute(MICROMETER_OBSERVATION_ATTRIBUTE_KEY, observation);
        mutablePropagatedContext.add(new ObservationPropagationContext(observation));
    }

    @ResponseFilter
    public void response(HttpRequest<?> request, MutableHttpResponse<?> response) {
        request.getAttribute(MICROMETER_OBSERVATION_ATTRIBUTE_KEY).ifPresent(x -> {
            response.getAttribute(EXCEPTION).ifPresent(e -> ((Observation) x).error((Throwable) e));
            ((ServerRequestObservationContext) ((Observation) x).getContext()).setResponse(response);
            ((Observation) x).stop();
        });
    }
}
