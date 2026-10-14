/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.micrometer.benchmarks.Contexts;
import io.micronaut.micrometer.benchmarks.MetricsMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

/**
 * The {@code ServerMetricsFilter} bean of a {@link MetricsMode} context, called outside of the server with
 * a request carrying a matched {@code /bench/{id}} route:
 * <ul>
 *     <li>{@link #resolvePath()}: the (private) URI template resolution, through a method handle;</li>
 *     <li>{@link #filter()}: {@code onRequest} + {@code onResponse}, i.e. the whole per-request cost of
 *     the filter methods (request state, path, tags, timer lookup and record), without the filter chain
 *     itself. The request state attribute is removed after each call, as the filter skips a request it
 *     has already seen.</li>
 * </ul>
 * The request is a client {@code MutableHttpRequest} (not the server's Netty request), so the attribute
 * access may differ slightly from the server, and the timer is recorded in the response filter (there is
 * no request lifecycle to defer it to). Each worker thread has its own request and response
 * ({@link Exchange}), so the request state of one worker is never seen by another.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ServerMetricsFilterBenchmark {

    private static final String REQUEST_METRICS_ATTRIBUTE = ServerMetricsFilter.class.getName() + ".REQUEST_METRICS";
    private static final MethodHandle RESOLVE_PATH;

    static {
        try {
            RESOLVE_PATH = MethodHandles.privateLookupIn(ServerMetricsFilter.class, MethodHandles.lookup())
                .findStatic(ServerMetricsFilter.class, "resolvePath", MethodType.methodType(String.class, HttpRequest.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Param({"METRICS", "METRICS_HISTOGRAM", "METRICS_PROMETHEUS"})
    public MetricsMode mode;

    private ApplicationContext context;
    private ServerMetricsFilter filter;

    @Setup(Level.Trial)
    public void setup() {
        context = Contexts.start(mode);
        filter = context.getBean(ServerMetricsFilter.class);
        Exchange exchange = new Exchange();
        exchange.setup(this);
        String path = resolvePath(exchange);
        if (!"/bench/{id}".equals(path)) {
            throw new IllegalStateException("Unexpected path " + path);
        }
        filter(exchange);
        Contexts.assertMeter(context, "http.server.requests", mode);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public String resolvePath(Exchange exchange) {
        try {
            return (String) RESOLVE_PATH.invokeExact((HttpRequest<?>) exchange.request);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Benchmark
    public void filter(Exchange exchange) {
        filter.onRequest(exchange.request);
        filter.onResponse(exchange.request, exchange.response);
        exchange.request.getAttributes().remove(REQUEST_METRICS_ATTRIBUTE);
    }

    /**
     * The request and response of a worker thread.
     */
    @State(Scope.Thread)
    public static class Exchange {

        private MutableHttpRequest<?> request;
        private MutableHttpResponse<?> response;

        @Setup(Level.Trial)
        public void setup(ServerMetricsFilterBenchmark benchmark) {
            request = Contexts.routedRequest(benchmark.context, "/bench/1");
            response = HttpResponse.ok("Hello 1");
        }
    }
}
