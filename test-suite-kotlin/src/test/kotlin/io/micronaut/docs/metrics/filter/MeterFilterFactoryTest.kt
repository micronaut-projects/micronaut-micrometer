package io.micronaut.docs.metrics.filter

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "MeterFilterFactoryTest")
@MicronautTest
class MeterFilterFactoryTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Test
    fun theCommonTagIsAddedToEveryMeter() {
        val counter = meterRegistry.counter("filter.test")

        assertEquals("demo", counter.id.getTag("scope"))
    }

    @Test
    fun theMethodTagOfTheServerRequestsMeterIsRenamed() {
        val timer = meterRegistry.timer("http.server.requests", "method", "GET")

        assertEquals("GET", timer.id.getTag("httpmethod"))
        assertNull(timer.id.getTag("method"))
    }
}
