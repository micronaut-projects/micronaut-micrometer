package io.micronaut.configuration.metrics.graal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class PrometheusEndpointTest {

    private static final int METER_COUNT = 2000;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testPrometheusEndpoint() {
        Counter.builder("native.prometheus.counter").register(meterRegistry).increment();

        String body = client.toBlocking().retrieve("/prometheus");

        assertNotNull(body);
        assertTrue(body.contains("native_prometheus_counter_total 1.0"), body);
        assertTrue(body.contains("jvm_memory_used_bytes"), body);
    }

    @Test
    void testLargePrometheusScrape() {
        for (int i = 0; i < METER_COUNT; i++) {
            Counter.builder("native.large.scrape.meter.with.a.long.name." + i).register(meterRegistry).increment();
        }

        for (int i = 0; i < 10; i++) {
            String body = client.toBlocking().retrieve("/prometheus");

            assertTrue(body.getBytes(StandardCharsets.UTF_8).length > 64 * 1024);
            assertEquals(METER_COUNT, body.lines().filter(line -> line.startsWith("native_large_scrape_meter_with_a_long_name_")).count());
        }
    }
}
