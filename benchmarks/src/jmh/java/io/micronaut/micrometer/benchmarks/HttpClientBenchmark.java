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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
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

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Requests sent by the Micronaut HTTP client to an embedded server running in a separate, uninstrumented
 * ({@link MetricsMode#NONE}) context. Only the client context uses the benchmark mode, so the difference
 * between the modes is the client-side overhead (the server is a constant offset).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HttpClientBenchmark {

    @Param({"NONE", "OBSERVATION_NOOP", "NOOP_HANDLER", "METRICS", "OBSERVATION", "OBSERVATION_NO_LTT"})
    public MetricsMode mode;

    private ApplicationContext serverContext;
    private ApplicationContext clientContext;
    private BenchClient declarativeClient;
    private HttpClient lowLevelClient;
    private BlockingHttpClient blockingClient;
    private HttpRequest<?> request;

    @Setup(Level.Trial)
    public void setup() {
        serverContext = Contexts.start(MetricsMode.NONE);
        EmbeddedServer server = serverContext.getBean(EmbeddedServer.class).start();
        clientContext = Contexts.start(mode, Map.of("bench.url", server.getURL().toString()));
        Contexts.assertClientFilters(clientContext, mode);
        declarativeClient = clientContext.getBean(BenchClient.class);
        lowLevelClient = clientContext.createBean(HttpClient.class, server.getURL());
        blockingClient = lowLevelClient.toBlocking();
        request = HttpRequest.GET("/bench/1").accept(MediaType.TEXT_PLAIN_TYPE);
        check(declarative());
        check(lowLevel());
        Contexts.assertMeter(clientContext, "http.client.requests", mode);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (lowLevelClient != null) {
            lowLevelClient.close();
        }
        if (clientContext != null) {
            clientContext.close();
        }
        if (serverContext != null) {
            serverContext.close();
        }
    }

    /**
     * @return the body, through the declarative client (templated {@code /bench/{id}})
     */
    @Benchmark
    public String declarative() {
        return declarativeClient.get("1");
    }

    /**
     * @return the body, through the low-level blocking client (no URI template)
     */
    @Benchmark
    public String lowLevel() {
        return blockingClient.retrieve(request);
    }

    private static void check(String body) {
        if (!"Hello 1".equals(body)) {
            throw new IllegalStateException("Unexpected response: " + body);
        }
    }
}
