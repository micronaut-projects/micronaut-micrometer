package io.micronaut.configuration.metrics.binder.web;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.annotation.Value;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.binder.web.WebMetricsPublisher.WEB_SERVER_METRICS_FILTER_ORDER;

/**
 * Factory to create order provides beans.
 */
@Factory
public final class FilterOrderProviderFactory {
    /**
     * Provide either the configured filter order or 0.
     * This can be overwritten because this is @Secondary
     *
     * @param order the order to inject. This is retrieved from the configuration
     * @return the order
     */
    @Bean
    @Singleton
    @Secondary
    public ServerRequestMeterRegistryFilterOrderProvider serverRequestMeterRegistryFilterOrderProvider(@Value("${" + WEB_SERVER_METRICS_FILTER_ORDER + ":0}") int order) {
        return () -> order;
    }
}
