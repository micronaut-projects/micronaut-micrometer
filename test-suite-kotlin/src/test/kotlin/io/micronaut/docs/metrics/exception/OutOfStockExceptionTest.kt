package io.micronaut.docs.metrics.exception

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "OutOfStockExceptionTest")
@MicronautTest
class OutOfStockExceptionTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Test
    fun theHandledExceptionIsReportedWithTheStatusOfItsResponse() {
        val response = client.toBlocking().exchange(HttpRequest.GET<Any>("/stock/apples"), Int::class.java)

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(0, response.body())

        val timer = meterRegistry.get("http.server.requests")
            .tags("uri", "/stock/{name}", "exception", "OutOfStockException", "status", "200")
            .timer()
        assertEquals(1, timer.count())
    }
}
