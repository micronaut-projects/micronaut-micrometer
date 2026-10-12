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
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.micrometer.observation.http.AbstractObservationFilter;
import io.micronaut.micrometer.observation.http.server.ObservationServerFilter;
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
 * The {@code ObservationServerFilter} bean of a {@link MetricsMode} context, called outside of the server
 * ({@code request} + {@code response}) with a request carrying a matched {@code /bench/{id}} route: the
 * observation context, start, propagated context element, key values and stop, without the filter chain.
 * {@code OBSERVATION_NOOP} runs against a registry without handlers, {@code NOOP_HANDLER} with a handler
 * that does nothing, {@code OBSERVATION} with the {@code DefaultMeterObservationHandler} ({@code OBSERVATION_NO_LTT}
 * without its long task timer). The observation attributes are removed after each call, as the filter skips a
 * request it has already seen.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ObservationServerFilterBenchmark {

    private static final String SERVER_OBSERVATION_ATTRIBUTE = ObservationServerFilter.class.getName() + ".SERVER_OBSERVATION";

    @Param({"OBSERVATION_NOOP", "NOOP_HANDLER", "OBSERVATION", "OBSERVATION_NO_LTT"})
    public MetricsMode mode;

    private ApplicationContext context;
    private ObservationServerFilter filter;
    private MutableHttpRequest<?> request;
    private MutableHttpResponse<?> response;

    @Setup(Level.Trial)
    public void setup() {
        context = Contexts.start(mode);
        Contexts.assertServerFilters(context, mode);
        filter = context.getBean(ObservationServerFilter.class);
        request = Contexts.routedRequest(context, "/bench/1");
        response = HttpResponse.ok("Hello 1");
        filter();
        Contexts.assertMeter(context, "http.server.requests", mode);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public MutablePropagatedContext filter() {
        MutablePropagatedContext propagatedContext = MutablePropagatedContext.of(PropagatedContext.empty());
        filter.request(request, propagatedContext);
        filter.response(request, response);
        request.getAttributes().remove(SERVER_OBSERVATION_ATTRIBUTE);
        request.getAttributes().remove(AbstractObservationFilter.MICROMETER_OBSERVATION_ATTRIBUTE_KEY);
        return propagatedContext;
    }
}
