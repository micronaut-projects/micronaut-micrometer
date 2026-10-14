package io.micronaut.docs.metrics.annotation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class OrderServiceTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    OrderService orderService;

    @Test
    void theMeterTagsAreAddedToTheTimer() {
        assertEquals("shipped", orderService.lookup("acme", new Order("eu")));

        var timer = meterRegistry.get("orders.lookup")
            .tags("status", "shipped", "label", "tenant-acme", "tenant", "acme", "region", "eu")
            .timer();
        assertEquals(1, timer.count());
    }
}
