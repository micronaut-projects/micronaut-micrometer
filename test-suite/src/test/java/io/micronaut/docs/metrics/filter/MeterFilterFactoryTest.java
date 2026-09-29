package io.micronaut.docs.metrics.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Property(name = "spec.name", value = "MeterFilterFactoryTest")
@MicronautTest
class MeterFilterFactoryTest {

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void theCommonTagIsAddedToEveryMeter() {
        var counter = meterRegistry.counter("filter.test");

        assertEquals("demo", counter.getId().getTag("scope"));
    }

    @Test
    void theMethodTagOfTheServerRequestsMeterIsRenamed() {
        var timer = meterRegistry.timer("http.server.requests", "method", "GET");

        assertEquals("GET", timer.getId().getTag("httpmethod"));
        assertNull(timer.getId().getTag("method"));
    }
}
