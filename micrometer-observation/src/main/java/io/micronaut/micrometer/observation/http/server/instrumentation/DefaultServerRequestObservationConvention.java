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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.UriRouteInfo;

import java.util.Optional;

/**
 * Default {@link ServerRequestObservationConvention}.
 */
@Internal
public final class DefaultServerRequestObservationConvention implements ServerRequestObservationConvention {
    private static final String DEFAULT_NAME = "http.server.requests";

    private static final KeyValue STATUS_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "UNKNOWN");

    private static final KeyValue URI_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "UNKNOWN");

    private static final KeyValue URI_NOT_FOUND = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "NOT_FOUND");

    private static final KeyValue URI_REDIRECTION = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "REDIRECTION");

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    private final String name;

    /**
     * Create a convention with the default name {@code "http.server.requests"}.
     */
    public DefaultServerRequestObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * Create a convention with a custom name.
     * @param name the observation name
     */
    public DefaultServerRequestObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(ServerRequestObservationContext context) {
        String httpMethod = context.getCarrier().getMethod().name().toLowerCase();
        String route = getRoute(context.getCarrier());

        if (route != null) {
            return "http " + httpMethod + " " + route;
        }
        return "http " + httpMethod;
    }

    private String getRoute(HttpRequest<?> request) {
        Optional<String> routeInfo = request.getAttribute(HttpAttributes.ROUTE_INFO)
            .filter(UriRouteInfo.class::isInstance)
            .map(ri -> (UriRouteInfo<?, ?>) ri)
            .map(UriRouteInfo::getUriMatchTemplate)
            .map(UriMatchTemplate::toPathString);
        return routeInfo.orElseGet(() ->
            request.getAttribute(HttpAttributes.URI_TEMPLATE)
                .map(Object::toString)
                .orElse(null)
        );
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        return KeyValues.of(exception(context), method(context), status(context), uri(context));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
        return KeyValues.of(httpUrl(context));
    }

    private KeyValue method(ServerRequestObservationContext context) {
        return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, context.getCarrier().getMethod().name());
    }

    private KeyValue status(ServerRequestObservationContext context) {
        return (context.getResponse() != null) ?
            KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, Integer.toString(context.getResponse().getStatus().getCode())) :
            STATUS_UNKNOWN;
    }

    private KeyValue uri(ServerRequestObservationContext context) {
            String route = getRoute(context.getCarrier());
            if (route != null) {
                return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, route);
            }
            if (context.getResponse() != null) {
                HttpStatus status = context.getResponse().getStatus();
                if (status != null) {
                    if (299 < status.getCode() && status.getCode() < 400) {
                        return URI_REDIRECTION;
                    }
                    if (status == HttpStatus.NOT_FOUND) {
                        return URI_NOT_FOUND;
                    }
                }
            }
            return URI_UNKNOWN;
    }

    private KeyValue exception(ServerRequestObservationContext context) {
        Throwable error = context.getError();
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION,
                StringUtils.hasText(simpleName) ? simpleName : error.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    private KeyValue httpUrl(ServerRequestObservationContext context) {
        return KeyValue.of(ServerHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, context.getCarrier().getUri().toString());
    }

}
