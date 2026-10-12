package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.aop.MeterTag;

interface MeterTagApi {

    Integer fromInterface(@MeterTag("region") String region);
}
