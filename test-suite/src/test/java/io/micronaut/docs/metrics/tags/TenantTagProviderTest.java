package io.micronaut.docs.metrics.tags;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Property(name = "spec.name", value = "TenantTagProviderTest")
@MicronautTest
class TenantTagProviderTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void theTenantTagIsAddedToTheHttpMetrics() {
        client.toBlocking().exchange(HttpRequest.GET("/tenants").header("X-Tenant", "acme"), String.class);

        assertNotNull(meterRegistry.find("http.client.requests").tags("tenant", "acme").timer());

        // the server timer is recorded once the response was written
        var search = meterRegistry.find("http.server.requests").tags("uri", "/tenants", "tenant", "acme");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (search.timer() == null && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        var timer = search.timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Requires(property = "spec.name", value = "TenantTagProviderTest")
    @Controller("/tenants")
    static class TenantController {

        @Get
        String index() {
            return "ok";
        }
    }
}
