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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

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

        // the server timer is recorded once the response was written
        val search = meterRegistry.find("http.server.requests")
            .tags("uri", "/stock/{name}", "exception", "OutOfStockException", "status", "200")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (search.timer() == null && System.nanoTime() < deadline) {
            Thread.onSpinWait()
        }
        val timer = search.timer()
        assertNotNull(timer)
        assertEquals(1, timer!!.count())
    }
}
