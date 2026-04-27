package io.micronaut.configuration.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;


@MicronautTest
@Property(name = "spec.name", value = "WebMetricsStatusCodeTest")
@Property(name = "micronaut.metrics.binders.web.enabled", value = StringUtils.TRUE)
class WebMetricsExceptionCodeTest {

    @Test
    void testWebMetricsCustomStatusCode(@Client("/") HttpClient httpClient, MeterRegistry meterRegistry) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () ->  client.exchange( "/metrics/test"));
        Assertions.assertEquals(HttpStatus.GATEWAY_TIMEOUT, e.getStatus());
        Assertions.assertEquals("Client '/': Gateway Timeout", e.getMessage());
        long count = meterRegistry.timer("http.server.requests", List.of(Tag.of("method", "GET"), Tag.of("uri", "/metrics/test"), Tag.of("status", "504"), Tag.of("exception", "GatewayTimeoutException"))).count();
        Assertions.assertEquals(1, count);
    }

    @Test
    void testWebMetricsBadRequestStatusCode(@Client("/") HttpClient httpClient, MeterRegistry meterRegistry) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> client.exchange("/metrics/bad-request"));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        Timer timer = meterRegistry.find("http.server.requests")
            .tags("status", "400", "exception", "BadRequestException")
            .timer();
        Assertions.assertNotNull(timer);
        Assertions.assertEquals(1, timer.count());
        Assertions.assertNull(meterRegistry.find("http.server.requests")
            .tags("status", "500", "exception", "BadRequestException")
            .timer());
    }

    @Controller("/metrics")
    @Requires(property = "spec.name", value = "WebMetricsStatusCodeTest")
    static class WebMetricsCustomStatusCodeController {

        @Get("/test")
        String test() {
            throw new GatewayTimeoutException("GATEWAY TIMEOUT");
        }

        @Get("/bad-request")
        String badRequest() {
            throw new BadRequestException("BAD REQUEST");
        }
    }

    static class GatewayTimeoutException extends RuntimeException {
        public GatewayTimeoutException(String message) {
            super(message);
        }
    }

    static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    @Produces
    @Singleton
    @Requires(property = "spec.name", value = "WebMetricsStatusCodeTest")
    static class GatewayTimeoutExceptionHandler implements ExceptionHandler<GatewayTimeoutException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, GatewayTimeoutException exception) {
            return HttpResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(exception.getMessage());
        }
    }

    @Produces
    @Singleton
    @Requires(property = "spec.name", value = "WebMetricsStatusCodeTest")
    static class BadRequestExceptionHandler implements ExceptionHandler<BadRequestException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, BadRequestException exception) {
            return HttpResponse.status(HttpStatus.BAD_REQUEST)
                .body(exception.getMessage());
        }
    }

}
