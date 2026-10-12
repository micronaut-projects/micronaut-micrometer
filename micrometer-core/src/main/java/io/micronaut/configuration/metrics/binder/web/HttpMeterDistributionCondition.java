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

import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig;
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * Matches when one of the distribution properties of the HTTP server or client meters
 * ({@code percentiles}, {@code histogram}, {@code slos}, {@code min} or {@code max}) is set, so
 * that the {@link HttpMeterFilterFactory} filters are not created by other properties under the
 * same prefix, such as {@code max-uri-tags}.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Internal
public abstract sealed class HttpMeterDistributionCondition implements Condition
    permits HttpMeterDistributionCondition.Server, HttpMeterDistributionCondition.Client {

    private static final List<String> KEYS = List.of("percentiles", "histogram", "slos", "min", "max");

    private final String prefix;

    private HttpMeterDistributionCondition(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public boolean matches(ConditionContext context) {
        for (String key : KEYS) {
            String property = prefix + '.' + key;
            if (context.containsProperty(property) || context.containsProperties(property)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The condition for the {@code http.server.requests} meters.
     */
    @Internal
    public static final class Server extends HttpMeterDistributionCondition {

        /**
         * Default constructor.
         */
        public Server() {
            super(HttpServerMeterConfig.PATH);
        }
    }

    /**
     * The condition for the {@code http.client.requests} meters.
     */
    @Internal
    public static final class Client extends HttpMeterDistributionCondition {

        /**
         * Default constructor.
         */
        public Client() {
            super(HttpClientMeterConfig.PATH);
        }
    }
}
