/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.configuration.metrics.binder.web.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Http server meter configuration.
 *
 * @since 5.6.0
 */
@ConfigurationProperties(HttpServerMeterConfig.PATH)
public class HttpServerMeterConfig extends HttpMeterConfig {

    /**
     * To config path.
     */
    public static final String PATH = HttpMetricsConfig.PATH + ".server";

    /**
     * The metric.
     */
    public static final String REQUESTS_METRIC = "http.server.requests";
}
