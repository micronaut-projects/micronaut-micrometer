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
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteInfo;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link ServerRequestObservationConvention}.
 */
@Internal
public final class DefaultServerRequestObservationConvention implements ServerRequestObservationConvention {
    /**
     * The maximum number of cached route paths.
     */
    static final int MAX_ROUTE_PATHS = 1024;

    private static final String DEFAULT_NAME = "http.server.requests";

    private static final KeyValue STATUS_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "UNKNOWN");

    private static final KeyValue URI_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "UNKNOWN");

    private static final KeyValue URI_NOT_FOUND = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "NOT_FOUND");

    private static final KeyValue URI_REDIRECTION = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "REDIRECTION");

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    private static final int MIN_CACHED_STATUS = 100;

    // the key values and lower case names that would otherwise be created for each request
    private static final KeyValue[] STATUS_KEY_VALUES = new KeyValue[500];

    private static final KeyValue[] METHOD_KEY_VALUES = new KeyValue[HttpMethod.values().length];

    private static final String[] LOWER_CASE_METHODS = new String[HttpMethod.values().length];

    static {
        for (int i = 0; i < STATUS_KEY_VALUES.length; i++) {
            STATUS_KEY_VALUES[i] = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, Integer.toString(MIN_CACHED_STATUS + i));
        }
        for (HttpMethod method : HttpMethod.values()) {
            METHOD_KEY_VALUES[method.ordinal()] = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, method.name());
            LOWER_CASE_METHODS[method.ordinal()] = method.name().toLowerCase();
        }
    }

    private final String name;

    // UriMatchTemplate#toPathString computes the path on every call. Equal templates of different routes share
    // an entry, the default convention is shared by the contexts of the JVM (the cache holds no route or bean) and
    // the cache stops growing at MAX_ROUTE_PATHS entries, the routes of an application being a fixed set.
    private final Map<UriMatchTemplate, String> routePaths = new ConcurrentHashMap<>();

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
        String httpMethod = LOWER_CASE_METHODS[context.getCarrier().getMethod().ordinal()];
        String route = getRoute(context.getCarrier());

        if (route != null) {
            return "http " + httpMethod + " " + route;
        }
        return "http " + httpMethod;
    }

    private @Nullable String getRoute(HttpRequest<?> request) {
        if (RouteAttributes.getRouteInfo(request).orElse(null) instanceof UriRouteInfo<?, ?> uriRouteInfo) {
            return routePath(uriRouteInfo.getUriMatchTemplate());
        }
        return BasicHttpAttributes.getUriTemplate(request).orElse(null);
    }

    /**
     * @param template The route template
     * @return The path of the template, see {@link UriMatchTemplate#toPathString()}
     */
    String routePath(UriMatchTemplate template) {
        String path = routePaths.get(template);
        if (path == null) {
            path = template.toPathString();
            if (routePaths.size() < MAX_ROUTE_PATHS) {
                routePaths.putIfAbsent(template, path);
            }
        }
        return path;
    }

    /**
     * @return The number of cached route paths
     */
    int routePathCount() {
        return routePaths.size();
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
        return METHOD_KEY_VALUES[context.getCarrier().getMethod().ordinal()];
    }

    private KeyValue status(ServerRequestObservationContext context) {
        if (context.getResponse() == null) {
            return STATUS_UNKNOWN;
        }
        int code = context.getResponse().code();
        int index = code - MIN_CACHED_STATUS;
        if (index >= 0 && index < STATUS_KEY_VALUES.length) {
            return STATUS_KEY_VALUES[index];
        }
        return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, Integer.toString(code));
    }

    private KeyValue uri(ServerRequestObservationContext context) {
            String route = getRoute(context.getCarrier());
            if (route != null) {
                return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, route);
            }
            if (context.getResponse() != null) {
                int code = context.getResponse().code();
                if (299 < code && code < 400) {
                    return URI_REDIRECTION;
                }
                if (code == HttpStatus.NOT_FOUND.getCode()) {
                    return URI_NOT_FOUND;
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
