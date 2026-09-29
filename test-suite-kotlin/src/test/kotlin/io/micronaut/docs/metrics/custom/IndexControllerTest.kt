package io.micronaut.docs.metrics.custom

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class IndexControllerTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Test
    fun theCustomCounterIsIncrementedOnEveryRequest() {
        assertEquals("Hello Fred", client.toBlocking().retrieve("/hello/Fred"))

        val counter = meterRegistry.get("web.access")
            .tags("controller", "index", "action", "hello")
            .counter()
        assertEquals(1.0, counter.count())
    }
}
