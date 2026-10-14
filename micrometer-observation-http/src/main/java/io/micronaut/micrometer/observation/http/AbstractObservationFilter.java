/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.micrometer.observation.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.LifecycleHttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Abstract filter used for Micrometer Observation.
 */
@Internal
public abstract class AbstractObservationFilter {

    public static final String CLIENT_PATH = "${micrometer.observation.http.client.path:/**}";
    public static final String SERVER_PATH = "${micrometer.observation.http.server.path:/**}";
    public static final String MICROMETER_OBSERVATION_ATTRIBUTE_KEY = "micrometer.observation";

    private static final String WEBSOCKET = "websocket";

    @Nullable
    private final Predicate<String> pathExclusionTest;

    /**
     * Configure observation in the filter for span creation and propagation across
     * arbitrary transports.
     *
     * @param pathExclusionTest for excluding URI paths from observation
     */
    protected AbstractObservationFilter(@Nullable Predicate<String> pathExclusionTest) {
        this.pathExclusionTest = pathExclusionTest;
    }

    /**
     * Tests if the defined path should be excluded from observation.
     *
     * @param path the path
     * @return {@code true} if the path should be excluded
     */
    protected boolean shouldExclude(@Nullable String path) {
        return pathExclusionTest != null && path != null && pathExclusionTest.test(path);
    }

    /**
     * The server request that runs its disposal resources once it is released, after its response
     * was written (see {@link LifecycleHttpRequest#addDisposalResource}). A WebSocket upgrade
     * request is not released when the connection is upgraded, so it has none.
     *
     * @param request The request, or a wrapper of it
     * @return The lifecycle of the request, or {@code null}
     */
    @Nullable
    @SuppressWarnings("java:S1452") // the request body type is unknown here and callers only add disposal resources
    protected static LifecycleHttpRequest<?> lifecycle(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (!(current instanceof LifecycleHttpRequest<?>)) {
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
        String upgrade = request.getHeaders().get(HttpHeaders.UPGRADE);
        if (upgrade != null && upgrade.equalsIgnoreCase(WEBSOCKET)) {
            return null;
        }
        return (LifecycleHttpRequest<?>) current;
    }
}
