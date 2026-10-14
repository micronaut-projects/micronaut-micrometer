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
import io.micrometer.core.instrument.Tags;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CancellationException;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Records the web filter timers for success and error conditions.
 *
 * @author Christian Oestreich
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class WebMetricsHelper {

    /**
     * The status of a request that has no response, e.g. because the client failed to connect.
     */
    static final int NO_STATUS = -1;

    /**
     * The {@code serviceId} tag value of a client request without a service id, so that the
     * client timers always have the same tag keys.
     */
    static final String NO_SERVICE_ID = "none";

    private static final String UNKNOWN = "UNKNOWN";
    private static final String METHOD = "method";
    private static final String STATUS = "status";
    private static final String URI = "uri";
    private static final String EXCEPTION = "exception";
    private static final String SERVICE_ID = "serviceId";

    private static final Tag URI_NOT_FOUND = Tag.of(URI, "NOT_FOUND");
    private static final Tag URI_REDIRECTION = Tag.of(URI, "REDIRECTION");
    private static final Tag URI_UNAUTHORIZED = Tag.of(URI, "UNAUTHORIZED");
    private static final Tag URI_BAD_REQUEST = Tag.of(URI, "BAD_REQUEST");
    private static final Tag EXCEPTION_NONE = Tag.of(EXCEPTION, "none");
    private static final Tag STATUS_CLIENT_ERROR = Tag.of(STATUS, "CLIENT_ERROR");
    private static final HttpMetricsTagProvider[] NO_TAG_PROVIDERS = new HttpMetricsTagProvider[0];
    private static final int MIN_CACHED_STATUS = 100;
    private static final Tag[] STATUS_TAGS = new Tag[500];

    static {
        for (int i = 0; i < STATUS_TAGS.length; i++) {
            STATUS_TAGS[i] = Tag.of(STATUS, String.valueOf(MIN_CACHED_STATUS + i));
        }
    }

    private WebMetricsHelper() {
    }

    /**
     * Records the timer of a request.
     *
     * @param meterRegistry         The meter registry
     * @param metricName            The metric name
     * @param start                 The start of the request, see {@link System#nanoTime()}
     * @param httpMethod            The HTTP method name
     * @param requestPath           The request path template, or {@code null} if it is unknown
     * @param serviceId             The ID of the service called in the request, or {@code null} for no tag
     * @param reportClientErrorURIs Whether client errors provide uris or not
     * @param status                The status of the response, or {@link #NO_STATUS} if there is none
     * @param exception             The exception to tag, or {@code null}
     * @param providedTags          The tags of the {@link HttpMetricsTagProvider}s, or {@code null}
     */
    @SuppressWarnings("java:S107") // the tags of a request timer, without an intermediate object
    static void recordTimer(MeterRegistry meterRegistry,
                       String metricName,
                       long start,
                       String httpMethod,
                       @Nullable String requestPath,
                       @Nullable String serviceId,
                       boolean reportClientErrorURIs,
                       int status,
                       @Nullable Throwable exception,
                       @Nullable Tags providedTags) {
        Tag method = Tag.of(METHOD, httpMethod);
        Tag statusTag = status == NO_STATUS ? status(exception) : status(status);
        Tag uri = uri(status, requestPath, reportClientErrorURIs);
        Tag exceptionTag = exception(exception);
        Tags tags = serviceId == null
            ? Tags.of(method, statusTag, uri, exceptionTag)
            : Tags.of(method, statusTag, uri, exceptionTag, Tag.of(SERVICE_ID, serviceId));
        if (providedTags != null) {
            tags = tags.and(providedTags);
        }
        meterRegistry.timer(metricName, tags).record(System.nanoTime() - start, NANOSECONDS);
    }

    /**
     * @param tagProviders The tag providers
     * @return The providers as an array, empty if there are none
     */
    static HttpMetricsTagProvider[] tagProviders(@Nullable List<HttpMetricsTagProvider> tagProviders) {
        return tagProviders == null || tagProviders.isEmpty()
            ? NO_TAG_PROVIDERS
            : tagProviders.toArray(NO_TAG_PROVIDERS);
    }

    /**
     * Collects the tags of the providers.
     *
     * @param tagProviders The tag providers, not empty
     * @param request      The request
     * @param response     The response, or {@code null} if there is none
     * @param throwable    The failure of the request, or {@code null}
     * @return The tags
     */
    static Tags providedTags(HttpMetricsTagProvider[] tagProviders,
                             HttpRequest<?> request,
                             @Nullable HttpResponse<?> response,
                             @Nullable Throwable throwable) {
        Tags tags = Tags.empty();
        for (HttpMetricsTagProvider tagProvider : tagProviders) {
            Iterable<Tag> provided = tagProvider.getTags(request, response, throwable);
            if (provided != null) {
                tags = tags.and(provided);
            }
        }
        return tags;
    }

    /**
     * @param status The HTTP status code
     * @return The status tag
     */
    private static Tag status(int status) {
        int index = status - MIN_CACHED_STATUS;
        if (index >= 0 && index < STATUS_TAGS.length) {
            return STATUS_TAGS[index];
        }
        return Tag.of(STATUS, String.valueOf(status));
    }

    /**
     * @param throwable The failure of a request without a response
     * @return The status tag
     */
    private static Tag status(@Nullable Throwable throwable) {
        if (throwable instanceof HttpStatusException httpStatusException) {
            return status(httpStatusException.getStatus().getCode());
        }
        if (throwable instanceof CancellationException) {
            return STATUS_CLIENT_ERROR;
        }
        return status(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    }

    /**
     * @param status                The status of the response, or {@link #NO_STATUS}
     * @param path                  The path of the request
     * @param reportClientErrorURIs Whether client errors provide uris or not
     * @return The URI tag
     */
    @NonNull
    private static Tag uri(int status, @Nullable String path, boolean reportClientErrorURIs) {
        if (status != NO_STATUS) {
            if (status >= 300 && status < 400) {
                return URI_REDIRECTION;
            }
            if (!reportClientErrorURIs && status >= 400 && status < 500) {
                if (status == HttpStatus.UNAUTHORIZED.getCode()) {
                    return URI_UNAUTHORIZED;
                }
                return URI_BAD_REQUEST;
            }
            if (status == HttpStatus.NOT_FOUND.getCode()) {
                return URI_NOT_FOUND;
            }
        }
        return Tag.of(URI, sanitizePath(path));
    }

    /**
     * @param throwable The exception, or {@code null}
     * @return The tag of the exception class name
     */
    private static Tag exception(@Nullable Throwable throwable) {
        if (throwable == null) {
            return EXCEPTION_NONE;
        }
        return Tag.of(EXCEPTION, throwable.getClass().getSimpleName());
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
}
