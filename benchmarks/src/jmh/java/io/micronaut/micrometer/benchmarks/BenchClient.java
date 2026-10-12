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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

/**
 * A declarative client of {@link BenchController}, used by {@link HttpClientBenchmark}. The templated
 * path sets the {@code URI_TEMPLATE} request attribute, which becomes the {@code uri} tag.
 */
@Client("${bench.url}")
@Requires(property = "bench.url")
public interface BenchClient {

    /**
     * @param id a path variable
     * @return the body
     */
    @Get("/bench/{id}")
    @Consumes(MediaType.TEXT_PLAIN)
    String get(String id);
}
