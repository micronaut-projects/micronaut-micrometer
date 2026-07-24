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
package io.micronaut.configuration.metrics.binder.executor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Contributes metrics for executor service implementations that need custom binding.
 *
 * @since 6.0.0
 */
@Internal
public interface ExecutorServiceMetricsContributor {

    /**
     * @param executorService The executor service
     * @return Whether this contributor handles the executor service
     */
    boolean supports(ExecutorService executorService);

    /**
     * Bind metrics for the executor service.
     *
     * @param meterRegistry The meter registry
     * @param executorService The executor service
     * @param name The bean name
     * @param tags The tags
     * @return The executor service bean to keep
     */
    ExecutorService bindTo(MeterRegistry meterRegistry, ExecutorService executorService, String name, List<Tag> tags);
}
