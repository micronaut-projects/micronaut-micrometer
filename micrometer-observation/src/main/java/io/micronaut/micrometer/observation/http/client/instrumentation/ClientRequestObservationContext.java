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
package io.micronaut.micrometer.observation.http.client.instrumentation;

import io.micrometer.observation.transport.RequestReplySenderContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;

/**
 * Context that holds information for metadata collection
 * during the {@link ClientHttpObservationDocumentation#HTTP_CLIENT_EXCHANGES client HTTP exchanges} observations.
 * <p>This context also extends {@link RequestReplySenderContext} for propagating observation
 * information with the HTTP client exchange.
 */
public class ClientRequestObservationContext extends RequestReplySenderContext<MutableHttpRequest<?>, HttpResponse<?>> {

    /**
     * Create an observation context for {@link MutableHttpRequest} observations.
     * @param request the HTTP client request
     */
    public ClientRequestObservationContext(MutableHttpRequest<?> request) {
        super(ClientRequestObservationContext::setRequestHeader);
        this.setCarrier(request);
    }

    private static void setRequestHeader(@Nullable MutableHttpRequest<?> request, String name, String value) {
        if (request != null) {
            request.getHeaders().set(name, value);
        }
    }

}
