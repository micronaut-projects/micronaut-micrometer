package io.micronaut.docs.metrics.configurer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Property(name = "spec.name", value = "SimpleMeterRegistryConfigurerTest")
@MicronautTest
class SimpleMeterRegistryConfigurerTest {

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void theConfigurerIsAppliedToTheSimpleMeterRegistry() {
        // the SimpleMeterRegistry is the default registry of the composite when no other registry is configured
        var composite = assertInstanceOf(CompositeMeterRegistry.class, meterRegistry);
        var simpleMeterRegistry = composite.getRegistries().stream()
                .filter(SimpleMeterRegistry.class::isInstance)
                .findFirst()
                .orElseThrow();

        meterRegistry.counter("configurer.test").increment();

        var counter = simpleMeterRegistry.get("configurer.test").tags("key", "value").counter();
        assertEquals(1.0, counter.count());
    }
}
