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
import io.micrometer.core.instrument.Tag;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.web.router.ErrorRouteInfo;
import io.micronaut.web.router.RouteMatch;

import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Deals with the web filter metrics for success and error conditions.
 *
 * @author Christian Oestreich
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class WebMetricsHelper {

    private static final String UNKNOWN = "UNKNOWN";

    private static final Tag URI_NOT_FOUND = Tag.of("uri", "NOT_FOUND");
    private static final Tag URI_REDIRECTION = Tag.of("uri", "REDIRECTION");
    private static final Tag URI_UNAUTHORIZED = Tag.of("uri", "UNAUTHORIZED");
    private static final Tag URI_BAD_REQUEST = Tag.of("uri", "BAD_REQUEST");
    private static final String METHOD = "method";
    private static final String STATUS = "status";
    private static final String URI = "uri";
    private static final String EXCEPTION = "exception";
    private static final String SERVICE_ID = "serviceId";

    private final MeterRegistry meterRegistry;
    private final String requestPath;
    private final long start;
    private final String httpMethod;
    private final String metricName;
    private final String serviceID;
    private final boolean reportClientErrorURIs;

    /**
     * @param meterRegistry         MeterRegistry bean
     * @param requestPath           The request path
     * @param start                 The start time of the request
     * @param httpMethod            The HTTP method name used
     * @param metricName            The metric name
     * @param serviceID             The ID of the service called in the request
     * @param reportClientErrorURIs Whether client errors provide uris or not
     */
    WebMetricsHelper(MeterRegistry meterRegistry,
                     String requestPath,
                     long start,
                     String httpMethod,
                     String metricName,
                     String serviceID,
                     boolean reportClientErrorURIs) {
        this.meterRegistry = meterRegistry;
        this.requestPath = requestPath;
        this.start = start;
        this.httpMethod = httpMethod;
        this.metricName = metricName;
        this.serviceID = serviceID;
        this.reportClientErrorURIs = reportClientErrorURIs;
    }

    /**
     * Get the tags for the metrics based on request shape.
     *
     * @param httpResponse The HTTP response
     * @param httpMethod   The name of the HTTP method (GET, POST, etc)
     * @param requestPath  The request path (/foo, /foo/bar, etc)
     * @param throwable    The throwable (optional)
     * @param serviceId         the service ID
     * @return A list of Tag objects
     */
    private static List<Tag> getTags(HttpResponse<?> httpResponse,
                                     String httpMethod,
                                     String requestPath,
                                     Throwable throwable,
                                     String serviceId,
                                     boolean reportClientErrorURIs) {
        List<@NonNull Tag> tags = new ArrayList<>(5);
        Tag t1 = method(httpMethod);
        if (t1 != null) {
            tags.add(t1);
        }
        tags.add(status(httpResponse, throwable));
        tags.add(uri(httpResponse, requestPath, reportClientErrorURIs));
        tags.add(exception(throwable));
        Tag t5 = serviceId(serviceId);
        if (t5 != null) {
            tags.add(t5);
        }
        return tags;
    }

    /**
     * Get a tag with the HTTP method name.
     *
     * @param httpMethod The name of the HTTP method.
     * @return Tag of method
     */
    @Nullable
    private static Tag method(@Nullable String httpMethod) {
        return httpMethod == null ? null : Tag.of(METHOD, httpMethod);
    }

    /**
     * Get a tag with the HTTP status value.
     *
     * @param httpResponse the HTTP response
     * @return Tag of status
     */
    private static Tag status(@Nullable HttpResponse<?> httpResponse, @Nullable Throwable throwable) {
        if (httpResponse == null) {
            if (throwable instanceof HttpStatusException httpStatusException) {
                return Tag.of(STATUS, String.valueOf(httpStatusException.getStatus().getCode()));
            }
            return Tag.of(STATUS, "500");
        }
        return Tag.of(STATUS, String.valueOf(httpResponse.code()));
    }

    /**
     * Get a tag with the URI.
     *
     * @param httpResponse the HTTP response
     * @param path         the path of the request
     * @return Tag of URI
     */
    @NonNull
    private static Tag uri(HttpResponse<?> httpResponse, String path, boolean reportClientErrorURIs) {
        if (httpResponse != null) {
            int code = httpResponse.code();
            if (code >= 300 && code < 400) {
                return URI_REDIRECTION;
            }
            if (!reportClientErrorURIs && code >= 400 && code < 500) {
                if (code == HttpStatus.UNAUTHORIZED.getCode()) {
                    return URI_UNAUTHORIZED;
                }
                return URI_BAD_REQUEST;
            }
            if (code == HttpStatus.NOT_FOUND.getCode()) {
                return URI_NOT_FOUND;
            }
        }
        return Tag.of(URI, sanitizePath(path));
    }

    /**
     * Get a tag with the throwable.
     *
     * @param throwable a throwable exception
     * @return Tag of exception class name
     */
    private static Tag exception(Throwable throwable) {
        if (throwable == null) {
            return Tag.of(EXCEPTION, "none");
        }
        return Tag.of(EXCEPTION, throwable.getClass().getSimpleName());
    }

    /**
     * Get a tag with the serviceId used in the call.
     *
     * @param serviceId The serviceId used in the call.
     * @return Tag of serviceId
     */
    @Nullable
    private static Tag serviceId(@Nullable String serviceId) {
        return serviceId == null ? null : Tag.of(SERVICE_ID, serviceId);
    }

    /**
     * Sanitize the URI path for double slashes and ending slashes.
     *
     * @param path the URI of the request
     * @return sanitized string
     */
    @NonNull
    static String sanitizePath(@Nullable String path) {
        if (path == null) {
            return UNKNOWN;
        }

        StringBuilder builder = null;
        // remove duplicate slashes
        for (int i = 0; i < path.length() - 1; i++) {
            if (path.charAt(i) == '/' && path.charAt(i + 1) == '/') {
                // need to do at least one substitution
                builder = new StringBuilder(path);
                builder.deleteCharAt(i + 1);
                for (; i < builder.length() - 1; i++) {
                    if (builder.charAt(i) == '/' && builder.charAt(i + 1) == '/') {
                        builder.deleteCharAt(i + 1);
                        i--;
                    }
                }
                break;
            }
        }
        if (builder != null) {
            // remove trailing slash
            if (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '/') {
                builder.setLength(builder.length() - 1);
            }
            path = builder.toString();
        } else {
            if (path.endsWith("/")) {
                // remove trailing slash
                path = path.substring(0, path.length() - 1);
            }
        }

        if (path.isEmpty()) {
            return "root";
        }
        return path;
    }

    /**
     * Registers the success timer for a web request.
     *
     * @param httpResponse the HTTP response
     */
    public void onResponse(HttpResponse<?> httpResponse) {
        Throwable throwable = httpResponse.getAttribute(HttpAttributes.EXCEPTION, Throwable.class).orElse(null);
        if (throwable != null) {
            RouteMatch<?> routeMatch = httpResponse.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
            if (routeMatch != null && routeMatch.getRouteInfo() instanceof ErrorRouteInfo<?, ?>) {
                // Avoid publishing an error on error route
                // Should this be configurable?
                success(httpResponse);
            } else {
                error(throwable);
            }
        } else {
            success(httpResponse);
        }
    }

    /**
     * Registers the success timer for a web request.
     *
     * @param httpResponse the HTTP response
     */
    public void success(HttpResponse<?> httpResponse) {
        List<Tag> tags = getTags(httpResponse, httpMethod, requestPath, null, serviceID, reportClientErrorURIs);
        meterRegistry.timer(metricName, tags)
            .record(System.nanoTime() - start, NANOSECONDS);
    }

    /**
     * Registers the error timer for a web request when an exception occurs.
     *
     * @param throwable exception that occurred
     */
    public void error(Throwable throwable) {
        HttpResponse<?> response = null;
        if (throwable instanceof HttpResponseProvider httpResponseProvider) {
            response = httpResponseProvider.getResponse();
        }
        List<Tag> tags = getTags(response, httpMethod, requestPath, throwable, serviceID, reportClientErrorURIs);
        meterRegistry.timer(metricName, tags)
            .record(System.nanoTime() - start, NANOSECONDS);
    }
}
