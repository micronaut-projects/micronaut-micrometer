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

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.micrometer.intercept.TimedInterceptor;
import io.micronaut.context.ApplicationContext;
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

import java.util.concurrent.TimeUnit;

/**
 * Calls of {@code @Timed} methods returning each supported type. Reactive results are consumed
 * ({@code block()} / {@code blockLast()}) so the timer is stopped. In {@link MetricsMode#NONE} there is no
 * meter registry and no interceptor, so the call is a plain method call.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class TimedBenchmark {

    @Param({"NONE", "METRICS", "METRICS_PROMETHEUS"})
    public MetricsMode mode;

    private ApplicationContext context;
    private TimedService service;

    @Setup(Level.Trial)
    public void setup() {
        context = Contexts.start(mode);
        boolean intercepted = context.containsBean(TimedInterceptor.class);
        if (intercepted != mode.metricsEnabled()) {
            throw new IllegalStateException("Unexpected interceptor presence " + intercepted + " for " + mode);
        }
        service = context.getBean(TimedService.class);
        sync();
        boolean recorded = context.findBean(MeterRegistry.class)
            .map(r -> r.find("bench.timed.sync").timer() != null)
            .orElse(false);
        if (recorded != mode.metricsEnabled()) {
            throw new IllegalStateException("Unexpected timer presence " + recorded + " for " + mode);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public String sync() {
        return service.sync("v");
    }

    @Benchmark
    public String syncExtraTags() {
        return service.syncExtraTags("v");
    }

    @Benchmark
    public String syncMeterTag() {
        return service.syncMeterTag("v");
    }

    @Benchmark
    public String syncMeterTagExpression() {
        return service.syncMeterTagExpression("v");
    }

    @Benchmark
    public String syncHistogram() {
        return service.syncHistogram("v");
    }

    @Benchmark
    public String completionStage() {
        return service.completionStage("v").toCompletableFuture().join();
    }

    @Benchmark
    public String mono() {
        return service.mono("v").block();
    }

    @Benchmark
    public String flux() {
        return service.flux("v").blockLast();
    }
}
