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
package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.Tag;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.jspecify.annotations.Nullable;

/**
 * Provides custom tags for HTTP client and server request metrics.
 *
 * @since 6.0.0
 */
@FunctionalInterface
public interface HttpMetricsTagProvider {

    /**
     * Resolve additional tags for an HTTP request metric.
     *
     * @param request The HTTP request
     * @param response The HTTP response, if available
     * @param throwable The request failure, if available
     * @return Additional tags for the request metric
     */
    Iterable<Tag> getTags(HttpRequest<?> request, @Nullable HttpResponse<?> response, @Nullable Throwable throwable);
}
