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
package io.micronaut.micrometer.observation.http.client;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.micrometer.observation.ObservationPropagationContext;
import io.micronaut.micrometer.observation.http.AbstractObservationFilter;
import io.micronaut.micrometer.observation.http.ObservationHttpExclusionsConfiguration;
import io.micronaut.micrometer.observation.http.client.instrumentation.ClientHttpObservationDocumentation;
import io.micronaut.micrometer.observation.http.client.instrumentation.ClientRequestObservationContext;
import io.micronaut.micrometer.observation.http.client.instrumentation.ClientRequestObservationConvention;
import io.micronaut.micrometer.observation.http.client.instrumentation.DefaultClientRequestObservationConvention;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.http.HttpAttributes.EXCEPTION;
import static io.micronaut.micrometer.observation.http.AbstractObservationFilter.CLIENT_PATH;

/**
 * An HTTP client instrumentation filter that uses Micrometer Observation API.
 */
@Internal
@ClientFilter(CLIENT_PATH)
@Requires(property = "micrometer.observation.http.server.enabled", notEquals = FALSE)
public final class ObservationClientFilter extends AbstractObservationFilter {

    private static final ClientRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultClientRequestObservationConvention();

    private final ObservationRegistry observationRegistry;

    private final ClientRequestObservationConvention observationConvention;

    public ObservationClientFilter(
        ObservationRegistry observationRegistry,
        @Nullable ClientRequestObservationConvention observationConvention,
        @Nullable ObservationHttpExclusionsConfiguration exclusionsConfig
    ) {
        super(exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
    }

    @RequestFilter
    public void request(MutableHttpRequest<?> request, MutablePropagatedContext mutablePropagatedContext) {
        if (shouldExclude(request.getPath())) {
            return;
        }
        ClientRequestObservationContext observationContext = new ClientRequestObservationContext(request);
        Observation observation = ClientHttpObservationDocumentation.HTTP_CLIENT_EXCHANGES.observation(this.observationConvention,
            DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry).start();
        request.setAttribute(MICROMETER_OBSERVATION_ATTRIBUTE_KEY, observation);
        mutablePropagatedContext.add(new ObservationPropagationContext(observation));
    }

    @ResponseFilter
    public void response(MutableHttpRequest<?> request, HttpResponse<?> response) {
        request.getAttribute(MICROMETER_OBSERVATION_ATTRIBUTE_KEY).ifPresent(x -> {
            response.getAttribute(EXCEPTION).ifPresent(e -> ((Observation) x).error((Throwable) e));
            ((ClientRequestObservationContext) ((Observation) x).getContext()).setResponse(response);
            ((Observation) x).stop();
        });
    }
}
