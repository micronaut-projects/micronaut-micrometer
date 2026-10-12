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
import io.micrometer.observation.ObservationRegistry;
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
 * Calls of {@code @Observed} methods returning each supported type, without a parent observation.
 * The {@code ObservedInterceptor} always exists (the {@code ObservationRegistry} bean is unconditional), so
 * in {@link MetricsMode#NONE} it runs against a registry without handlers (no-op observations);
 * {@link #plain()} is an unannotated method of the same bean, the zero baseline.
 * {@link MetricsMode#NOOP_HANDLER} adds a handler that does nothing, {@link MetricsMode#OBSERVATION}
 * records a timer through the {@code DefaultMeterObservationHandler}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ObservedBenchmark {

    @Param({"NONE", "NOOP_HANDLER", "OBSERVATION", "OBSERVATION_NO_LTT"})
    public MetricsMode mode;

    private ApplicationContext context;
    private ObservedService service;

    @Setup(Level.Trial)
    public void setup() {
        context = Contexts.start(mode);
        boolean noop = context.getBean(ObservationRegistry.class).isNoop();
        if (noop != (mode == MetricsMode.NONE)) {
            throw new IllegalStateException("Unexpected no-op registry " + noop + " for " + mode);
        }
        service = context.getBean(ObservedService.class);
        sync();
        boolean recorded = context.findBean(MeterRegistry.class)
            .map(r -> r.find("bench.observed").timer() != null)
            .orElse(false);
        if (recorded != mode.observationMeters()) {
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
    public String plain() {
        return service.plain("v");
    }

    @Benchmark
    public String sync() {
        return service.sync("v");
    }

    @Benchmark
    public String syncKeyValues() {
        return service.syncKeyValues("v");
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
