package io.micronaut.micrometer.observation.datasource;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("mn_product")
public record Product(
    @GeneratedValue
    @Id
    Long id,
    String name) {
}
