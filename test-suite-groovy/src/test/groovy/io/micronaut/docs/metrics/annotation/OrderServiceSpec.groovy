package io.micronaut.docs.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class OrderServiceSpec extends Specification {

    @Inject
    MeterRegistry meterRegistry

    @Inject
    OrderService orderService

    void "the meter tags are added to the timer"() {
        expect:
        orderService.lookup("acme", new Order("eu")) == "shipped"
        meterRegistry.get("orders.lookup")
            .tags("status", "shipped", "label", "tenant-acme", "tenant", "acme", "region", "eu")
            .timer()
            .count() == 1
    }
}
