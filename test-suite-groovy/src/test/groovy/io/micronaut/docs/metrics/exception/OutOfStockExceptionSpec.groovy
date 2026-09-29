package io.micronaut.docs.metrics.exception

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "OutOfStockExceptionSpec")
@MicronautTest
class OutOfStockExceptionSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    MeterRegistry meterRegistry

    void "the handled exception is reported with the status of its response"() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.GET("/stock/apples"), Integer)

        then:
        response.status() == HttpStatus.OK
        response.body() == 0

        when:
        def timer = meterRegistry.get("http.server.requests")
                .tags("uri", "/stock/{name}", "exception", "OutOfStockException", "status", "200")
                .timer()

        then:
        timer.count() == 1
    }
}
