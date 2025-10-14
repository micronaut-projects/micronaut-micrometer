package io.micronaut.configuration.metrics;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "micronaut.metrics.binders.web.enabled", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "WebMetricsCustomStatusCodeTest")
@MicronautTest
class WebMetricsCustomStatusCodeTest {

    @Test
    void testWebMetricsCustomStatusCode(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.GET("/customCode")));
        assertEquals(520, ex.getResponse().code());
    }

    @Requires(property = "spec.name", value = "WebMetricsCustomStatusCodeTest")
    @Controller
    static class WebMetricsCustomStatusCodeController {

        @Get("/customCode")
        HttpResponse<?> customCode() {
            return HttpResponse.status(520, " this is a custom code");
        }
    }
}
