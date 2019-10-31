package io.micronaut.configuration.metrics.binder.web;

import io.micronaut.http.filter.FilterOrderProvider;

/**
 * Provides an order for the {@link ServerRequestMeterRegistryFilter}.
 */
@FunctionalInterface
public interface ServerRequestMeterRegistryFilterOrderProvider extends FilterOrderProvider {
    @Override
    int getOrder();
}
