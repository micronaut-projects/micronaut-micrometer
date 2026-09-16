package io.micronaut.docs.metrics.custom;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class IndexControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void theCustomCounterIsIncrementedOnEveryRequest() {
        assertEquals("Hello Fred", client.toBlocking().retrieve("/hello/Fred"));

        var counter = meterRegistry.get("web.access")
                .tags("controller", "index", "action", "hello")
                .counter();
        assertEquals(1.0, counter.count());
    }
}
