package io.micronaut.configuration.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;


@MicronautTest
@Property(name = "spec.name", value = "WebMetricsExceptionWithGlobalErrorRouteTest")
@Property(name = "micronaut.metrics.binders.web.enabled", value = StringUtils.TRUE)
class WebMetricsExceptionWithGlobalErrorRouteTest {

    @Test
    void testWebMetricsSuccessGlobalErrorRoute(@Client("/") HttpClient httpClient, MeterRegistry meterRegistry) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () ->  client.exchange( "/metrics/test"));
        Assertions.assertEquals(e.getResponse().code(), HttpStatus.BAD_REQUEST.getCode());
        Assertions.assertEquals("BAD REQUEST FROM HANDLER", e.getMessage());
        long count = meterRegistry.timer("http.server.requests", List.of(Tag.of("method", "GET"), Tag.of("uri", "/metrics/test"), Tag.of("status", "400"), Tag.of("exception", "IllegalArgumentException"))).count();
        Assertions.assertEquals(1, count);
    }

    @Controller("/metrics")
    @Requires(property = "spec.name", value = "WebMetricsExceptionWithGlobalErrorRouteTest")
    static class WebMetricsCustomStatusCodeController {

        @Get("/test")
        String test() {
            throw new IllegalArgumentException("BAD REQUEST");
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "WebMetricsExceptionWithGlobalErrorRouteTest")
    static class ErrorHandler {

        @Error(exception = IllegalArgumentException.class, global = true)
        public HttpResponse<JsonError> unsupportedOperationExceptions(HttpRequest<?> request) {
            JsonError error = new JsonError("BAD REQUEST FROM HANDLER")
                .link(Link.SELF, Link.of(request.getUri()));
            return HttpResponse.<JsonError> badRequest().body(error);
        }
    }

}
