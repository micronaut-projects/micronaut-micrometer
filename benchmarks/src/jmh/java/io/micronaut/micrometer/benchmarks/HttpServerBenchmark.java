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
package io.micronaut.micrometer.benchmarks;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

/**
 * GET requests through the full Micronaut HTTP server filter chain (embedded Netty server), sent by the
 * JDK {@link HttpClient} over a kept-alive HTTP/1.1 connection. The client is not instrumented, so the
 * difference between the modes is the server-side overhead. {@code -prof gc} reports the allocations of
 * every thread of the JVM (client and server event loop), so the client cost is a constant offset across
 * the modes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HttpServerBenchmark {

    @Param({"NONE", "OBSERVATION_NOOP", "NOOP_HANDLER", "METRICS", "METRICS_HISTOGRAM", "METRICS_PROMETHEUS", "OBSERVATION", "OBSERVATION_NO_LTT"})
    public MetricsMode mode;

    private ApplicationContext context;
    private HttpClient client;
    private HttpRequest templated;
    private HttpRequest completionStage;
    private HttpRequest stream;

    @Setup(Level.Trial)
    public void setup() throws IOException, InterruptedException {
        context = Contexts.start(mode);
        Contexts.assertServerFilters(context, mode);
        EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        templated = request(server, "/bench/1");
        completionStage = request(server, "/bench/async/1");
        stream = request(server, "/bench/stream/1");
        check(templated(), "Hello 1");
        check(completionStage(), "Hello 1");
        check(stream(), "Hello 1!");
        Contexts.assertMeter(context, "http.server.requests", mode);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (client != null) {
            client.close();
        }
        if (context != null) {
            context.close();
        }
    }

    /**
     * @return the body of {@code GET /bench/{id}} (synchronous controller method)
     */
    @Benchmark
    public String templated() throws IOException, InterruptedException {
        return send(templated);
    }

    /**
     * @return the body of {@code GET /bench/async/{id}} (controller returning a completed {@code CompletionStage})
     */
    @Benchmark
    public String completionStage() throws IOException, InterruptedException {
        return send(completionStage);
    }

    /**
     * @return the body of {@code GET /bench/stream/{id}} (controller returning a 3 element {@code Flux}, chunked)
     */
    @Benchmark
    public String stream() throws IOException, InterruptedException {
        return send(stream);
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unexpected status " + response.statusCode());
        }
        return response.body();
    }

    private static HttpRequest request(EmbeddedServer server, String path) {
        return HttpRequest.newBuilder(URI.create(server.getURL() + path)).GET().build();
    }

    private static void check(String body, String expected) {
        if (!expected.equals(body)) {
            throw new IllegalStateException("Unexpected response: " + body);
        }
    }
}
