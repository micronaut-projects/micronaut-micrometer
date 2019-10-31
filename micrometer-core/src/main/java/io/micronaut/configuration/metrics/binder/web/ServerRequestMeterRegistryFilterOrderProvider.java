package io.micronaut.configuration.metrics.binder.web;

import io.micronaut.http.filter.FilterOrderProvider;

@FunctionalInterface
public interface ServerRequestMeterRegistryFilterOrderProvider extends FilterOrderProvider {
    @Override
    int getOrder();
}
