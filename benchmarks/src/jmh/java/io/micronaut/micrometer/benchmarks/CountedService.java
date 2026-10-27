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

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.aop.MeterTag;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * {@code @Counted} methods returning each supported type, used by {@link CountedBenchmark}.
 */
@Singleton
public class CountedService {

    /**
     * @param v a value
     * @return the value
     */
    @Counted("bench.counted.sync")
    public String sync(String v) {
        return v;
    }

    /**
     * @param v a value
     * @return the value
     */
    @Counted(value = "bench.counted.extra", extraTags = {"k1", "v1", "k2", "v2"})
    public String syncExtraTags(String v) {
        return v;
    }

    /**
     * @param v a value, tagged with its {@code toString()}
     * @return the value
     */
    @Counted("bench.counted.metertag")
    public String syncMeterTag(@MeterTag("arg") String v) {
        return v;
    }

    /**
     * @param v a value
     * @return a completed stage
     */
    @Counted("bench.counted.cs")
    public CompletionStage<String> completionStage(String v) {
        return CompletableFuture.completedFuture(v);
    }

    /**
     * @param v a value
     * @return a single value
     */
    @Counted("bench.counted.mono")
    public Mono<String> mono(String v) {
        return Mono.just(v);
    }

    /**
     * @param v a value
     * @return 3 values
     */
    @Counted("bench.counted.flux")
    public Flux<String> flux(String v) {
        return Flux.just(v, v, v);
    }
}
