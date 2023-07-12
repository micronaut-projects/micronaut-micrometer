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
package io.micronaut.micrometer.observation.http.server.instrumentation;

import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;

/**
 * Context that holds information for metadata collection regarding
 * {@link ServerHttpObservationDocumentation#HTTP_SERVER_REQUESTS Server HTTP requests} observations.
 * <p>This context also extends {@link RequestReplyReceiverContext} for propagating
 * observation information during HTTP request processing.
 */
@Internal
public final class ServerRequestObservationContext extends RequestReplyReceiverContext<HttpRequest<?>, MutableHttpResponse<?>> {

    public ServerRequestObservationContext(HttpRequest<?> request) {
        super(ServerRequestObservationContext::getRequestHeader);
        setCarrier(request);
    }

    private static String getRequestHeader(@Nullable HttpRequest<?> request, String name) {
        if (request != null) {
            return request.getHeaders().get(name);
        }
        return null;
    }

}
