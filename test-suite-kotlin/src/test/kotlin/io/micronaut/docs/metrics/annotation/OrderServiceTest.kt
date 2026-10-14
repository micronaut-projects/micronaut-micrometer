package io.micronaut.docs.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class OrderServiceTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Inject
    lateinit var orderService: OrderService

    @Test
    fun theMeterTagsAreAddedToTheTimer() {
        assertEquals("shipped", orderService.lookup("acme", Order("eu")))

        val timer = meterRegistry.get("orders.lookup")
            .tags("status", "shipped", "label", "tenant-acme", "tenant", "acme", "region", "eu")
            .timer()
        assertEquals(1, timer.count())
    }
}
