package io.micronaut.docs.metrics.exception;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "OutOfStockExceptionTest")
@MicronautTest
class OutOfStockExceptionTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void theHandledExceptionIsReportedWithTheStatusOfItsResponse() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/stock/apples"), Integer.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(0, response.body());

        var timer = meterRegistry.get("http.server.requests")
                .tags("uri", "/stock/{name}", "exception", "OutOfStockException", "status", "200")
                .timer();
        assertEquals(1, timer.count());
    }
}
