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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.UriMatchTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the path strings of the route templates ({@link UriMatchTemplate#toPathString()}), which the
 * template computes again on every call. Templates are equal when their template strings are, so equal
 * templates of different routes share an entry. The cache belongs to the filter bean, so it goes away with
 * its context, and it stops growing at {@link #MAX_ENTRIES} entries (the routes of an application are a
 * fixed set): the other templates are computed on every call, as without the cache.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Internal
final class RoutePaths {

    /**
     * The maximum number of cached templates.
     */
    static final int MAX_ENTRIES = 1024;

    private final Map<UriMatchTemplate, String> paths = new ConcurrentHashMap<>();

    /**
     * @param template The route template
     * @return The path of the template, see {@link UriMatchTemplate#toPathString()}
     */
    String pathString(UriMatchTemplate template) {
        String path = paths.get(template);
        if (path == null) {
            path = template.toPathString();
            if (paths.size() < MAX_ENTRIES) {
                paths.putIfAbsent(template, path);
            }
        }
        return path;
    }

    /**
     * @return The number of cached templates
     */
    int size() {
        return paths.size();
    }
}
