package io.micronaut.configuration.metrics.micrometer.prometheus.management;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;

import javax.inject.Inject;

/**
 * Adds a management endpoint for Prometheus.
 *
 * @author graemerocher
 * @since 1.1
 */
@Endpoint(PrometheusEndpoint.ID)
@RequiresMetrics
@Requires(beans = PrometheusMeterRegistry.class)
public class PrometheusEndpoint {

    public static final String ID = "prometheus";
    private PrometheusMeterRegistry prometheusMeterRegistry;

    /**
     * Default constructor
     * @param prometheusMeterRegistry The meter registry
     */
    @Inject
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    /**
     * Scrapes the data.
     * @return The data.
     */
    @Read
    public String scrape() {
        return prometheusMeterRegistry.scrape();
    }
}
