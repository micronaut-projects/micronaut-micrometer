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
import io.micrometer.core.instrument.Tag;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;
import io.micronaut.http.HttpStatus;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Deals with the web filter metrics for success and error conditions.
 *
 * @param <T> The response type
 * @author Christian Oestreich
 * @author graemerocher
 * @since 1.0
 */
@SuppressWarnings("PublisherImplementation")
public class WebMetricsPublisher<T extends HttpResponse<?>> extends Flux<T> {

    /**
     * To enable/disable web metrics.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".web.enabled";
    public static final String METRIC_HTTP_SERVER_REQUESTS = "http.server.requests";
    public static final String METRIC_HTTP_CLIENT_REQUESTS = "http.client.requests";

    private static final Tag URI_NOT_FOUND = Tag.of("uri", "NOT_FOUND");
    private static final Tag URI_REDIRECTION = Tag.of("uri", "REDIRECTION");
    private static final String UNKNOWN = "UNKNOWN";
    private static final String METHOD = "method";
    private static final String STATUS = "status";
    private static final String URI = "uri";
    private static final String EXCEPTION = "exception";
    private static final String HOST = "host";

    private final Flux<T> publisher;
    private final MeterRegistry meterRegistry;
    private final String requestPath;
    private final long start;
    private final String httpMethod;
    private final String metricName;
    private final String host;
    private final boolean reportErrors;

    /**
     * @param publisher     The original publisher
     * @param meterRegistry MeterRegistry bean
     * @param requestPath   The request path
     * @param start         The start time of the request
     * @param httpMethod    The HTTP method name used
     * @param isServer      Whether the metric relates to the server or the client
     * @param reportErrors  Whether errors should be reported
     */
    WebMetricsPublisher(Publisher<T> publisher,
                        MeterRegistry meterRegistry,
                        String requestPath,
                        long start,
                        String httpMethod,
                        boolean isServer,
                        boolean reportErrors) {
        this(publisher, meterRegistry, requestPath, start, httpMethod, isServer, null, reportErrors);
    }

    /**
     * @param publisher     The original publisher
     * @param meterRegistry MeterRegistry bean
     * @param requestPath   The request path
     * @param start         The start time of the request
     * @param httpMethod    The HTTP method name used
     * @param isServer      Whether the metric relates to the server or the client
     * @param host          The host called in the request
     * @param reportErrors  Whether errors should be reported
     */
    WebMetricsPublisher(Publisher<T> publisher,
                        MeterRegistry meterRegistry,
                        String requestPath,
                        long start,
                        String httpMethod,
                        boolean isServer,
                        String host,
                        boolean reportErrors) {
        this.publisher = Flux.from(publisher);
        this.meterRegistry = meterRegistry;
        this.requestPath = requestPath;
        this.start = start;
        this.httpMethod = httpMethod;
        this.metricName = isServer ? METRIC_HTTP_SERVER_REQUESTS : METRIC_HTTP_CLIENT_REQUESTS;
        this.host = host;
        this.reportErrors = reportErrors;
    }

    /**
     * @param publisher     The original publisher
     * @param meterRegistry MeterRegistry bean
     * @param requestPath   The request path
     * @param start         The start time of the request
     * @param httpMethod    The HTTP method name used
     * @param reportErrors  Whether errors should be reported
     */
    WebMetricsPublisher(Publisher<T> publisher,
                        MeterRegistry meterRegistry,
                        String requestPath,
                        long start,
                        String httpMethod,
                        boolean reportErrors) {
        this(publisher, meterRegistry, requestPath, start, httpMethod, true, null, reportErrors);
    }

    /**
     * Called for publisher.
     *
     * @param actual the original subscription
     */
    @SuppressWarnings("SubscriberImplementation")
    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {

        publisher.subscribe(new CoreSubscriber<T>() {

            @Override
            public Context currentContext() {
                return actual.currentContext();
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                actual.onSubscribe(subscription);
            }

            @Override
            public void onNext(T httpResponse) {
                success(httpResponse, start, httpMethod, requestPath, host);
                actual.onNext(httpResponse);
            }

            @Override
            public void onError(Throwable throwable) {
                if (reportErrors) {
                    error(start, httpMethod, requestPath, throwable, host);
                }
                actual.onError(throwable);
            }

            @Override
            public void onComplete() {
                actual.onComplete();
            }
        });
    }

    /**
     * Get the tags for the metrics based on request shape.
     *
     * @param httpResponse The HTTP response
     * @param httpMethod   The name of the HTTP method (GET, POST, etc)
     * @param requestPath  The request path (/foo, /foo/bar, etc)
     * @param throwable    The throwable (optional)
     * @param host         the host
     * @return A list of Tag objects
     */
    private static List<Tag> getTags(HttpResponse httpResponse,
                                     String httpMethod,
                                     String requestPath,
                                     Throwable throwable,
                                     String host) {
        return Stream
                .of(method(httpMethod), status(httpResponse), uri(httpResponse, requestPath), exception(throwable), host(host))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get a tag with the HTTP method name.
     *
     * @param httpMethod The name of the HTTP method.
     * @return Tag of method
     */
    private static Tag method(String httpMethod) {
        Tag tag = null;
        if (httpMethod != null) {
            tag = Tag.of(METHOD, httpMethod);
        }
        return tag;
    }

    /**
     * Get a tag with the HTTP status value.
     *
     * @param httpResponse the HTTP response
     * @return Tag of status
     */
    private static Tag status(HttpResponse httpResponse) {
        if (httpResponse == null) {
            return Tag.of(STATUS, "500");
        }

        HttpStatus status = httpResponse.status();
        if (status == null) {
            status = OK;
        }
        return Tag.of(STATUS, String.valueOf(status.getCode()));
    }

    /**
     * Get a tag with the URI.
     *
     * @param httpResponse the HTTP response
     * @param path         the path of the request
     * @return Tag of URI
     */
    private static Tag uri(HttpResponse httpResponse, String path) {
        if (httpResponse != null) {
            HttpStatus status = httpResponse.getStatus();
            if (status != null && status.getCode() >= 300 && status.getCode() < 400) {
                return URI_REDIRECTION;
            }
            if (status != null && status.equals(NOT_FOUND)) {
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
        if (throwable != null) {
            return Tag.of(EXCEPTION, throwable.getClass().getSimpleName());
        }
        return Tag.of(EXCEPTION, "none");
    }

    /**
     * Get a tag with the host used in the call.
     *
     * @param host The host used in the call.
     * @return Tag of host
     */
    private static Tag host(String host) {
        Tag tag = null;
        if (host != null) {
            tag = Tag.of(HOST, host);
        }
        return tag;
    }

    /**
     * Sanitize the URI path for double slashes and ending slashes.
     *
     * @param path the URI of the request
     * @return sanitized string
     */
    private static String sanitizePath(String path) {
        if (!StringUtils.isEmpty(path)) {
            path = path
                    .replaceAll("//+", "/")
                    .replaceAll("/$", "");
        }

        return path != null ? (path.isEmpty() ? "root" : path) : UNKNOWN;
    }

    /**
     * Registers the success timer for a web request.
     *
     * @param httpResponse the HTTP response
     * @param start        the start time of the request
     * @param httpMethod   the name of the HTTP method (GET, POST, etc)
     * @param requestPath  the URI of the request
     */
    private void success(HttpResponse httpResponse,
                         long start,
                         String httpMethod,
                         String requestPath,
                         String host) {
        Iterable<Tag> tags = getTags(httpResponse, httpMethod, requestPath, null, host);
        this.meterRegistry.timer(metricName, tags)
                .record(System.nanoTime() - start, NANOSECONDS);
    }

    /**
     * Registers the error timer for a web request when an exception occurs.
     *
     * @param start       the start time of the request
     * @param httpMethod  the name of the HTTP method (GET, POST, etc)
     * @param requestPath the URI of the request
     * @param throwable   exception that occurred
     */
    private void error(long start, String httpMethod, String requestPath,
                       Throwable throwable, String host) {
        HttpResponse response = null;
        if (throwable instanceof HttpResponseProvider) {
            response = ((HttpResponseProvider) throwable).getResponse();
        }
        Iterable<Tag> tags = getTags(response, httpMethod, requestPath, throwable, host);
        this.meterRegistry.timer(metricName, tags)
                .record(System.nanoTime() - start, NANOSECONDS);
    }
}
