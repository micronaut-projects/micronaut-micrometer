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

import io.micrometer.observation.annotation.Observed;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * {@code @Observed} methods returning each supported type, plus an unannotated method as the zero
 * baseline, used by {@link ObservedBenchmark}.
 */
@Singleton
public class ObservedService {

    /**
     * @param v a value
     * @return the value, without any interceptor
     */
    public String plain(String v) {
        return v;
    }

    /**
     * @param v a value
     * @return the value
     */
    @Observed(name = "bench.observed")
    public String sync(String v) {
        return v;
    }

    /**
     * @param v a value
     * @return the value
     */
    @Observed(name = "bench.observed", lowCardinalityKeyValues = {"k1", "v1", "k2", "v2"})
    public String syncKeyValues(String v) {
        return v;
    }

    /**
     * @param v a value
     * @return a completed stage
     */
    @Observed(name = "bench.observed")
    public CompletionStage<String> completionStage(String v) {
        return CompletableFuture.completedFuture(v);
    }

    /**
     * @param v a value
     * @return a single value
     */
    @Observed(name = "bench.observed")
    public Mono<String> mono(String v) {
        return Mono.just(v);
    }

    /**
     * @param v a value
     * @return 3 values
     */
    @Observed(name = "bench.observed")
    public Flux<String> flux(String v) {
        return Flux.just(v, v, v);
    }
}
