/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.configuration.metrics.micrometer.prometheus.management;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import jakarta.inject.Inject;

/**
 * Adds a management endpoint for Prometheus.
 *
 * @author graemerocher
 * @since 1.1
 */
@Endpoint(PrometheusEndpoint.ID)
public class PrometheusEndpoint {

    public static final String ID = "prometheus";
    private PrometheusMeterRegistry prometheusMeterRegistry;

    /**
     * @param prometheusMeterRegistry The meter registry
     */
    @Inject
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    /**
     * Scrapes the data.
     *
     * @return the data
     */
    @Read(produces = "text/plain; version=0.0.4")
    public String scrape() {
        return prometheusMeterRegistry.scrape();
    }
}
