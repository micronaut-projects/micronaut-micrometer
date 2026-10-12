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

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A trivial controller with templated routes (so the {@code uri} tag is resolved from the route), used
 * by {@link HttpServerBenchmark} and {@link HttpClientBenchmark}.
 */
@Controller("/bench")
public class BenchController {

    /**
     * @param id a path variable
     * @return a constant-size body
     */
    @Get("/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public String get(String id) {
        return "Hello " + id;
    }

    /**
     * @param id a path variable
     * @return a completed stage with a constant-size body
     */
    @Get("/async/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public CompletionStage<String> async(String id) {
        return CompletableFuture.completedFuture("Hello " + id);
    }

    /**
     * @param id a path variable
     * @return a streamed (chunked) body of 3 elements
     */
    @Get("/stream/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public Flux<String> stream(String id) {
        return Flux.just("Hello ", id, "!");
    }
}
