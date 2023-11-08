package io.micronaut.configuration.metrics

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "RoutePathVariableNotFoundMetricsSpec")
@MicronautTest
class NotAcceptableRouteWithPathVariableDoesNotPolluteMetricsSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    void 'sending multiple requests to a route with a path variable does not pollute the metrics uri'() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        client.exchange(HttpRequest.GET("/books/1"))

        then:
        noExceptionThrown()

        when:
        client.exchange(HttpRequest.GET("/books/2"))

        then:
        noExceptionThrown()

        when:
        List<String> uris = fetchHttpServerRequestMetricsUris(client)

        then:
        noExceptionThrown()
        uris == ['/books/{id}']

        when:
        client.exchange(HttpRequest.GET("/books/1").accept(MediaType.TEXT_PLAIN))

        then:
        HttpClientResponseException e = thrown()
        HttpStatus.NOT_ACCEPTABLE == e.status

        when:
        client.exchange(HttpRequest.GET("/books/2").accept(MediaType.TEXT_PLAIN))

        then:
        e = thrown()
        HttpStatus.NOT_ACCEPTABLE == e.status

        when:
        uris = fetchHttpServerRequestMetricsUris(client)

        then:
        noExceptionThrown()
        ['NO_ROUTE_MATCH', '/metrics/{name}','/books/{id}'] == uris
    }

    private static List<String> fetchHttpServerRequestMetricsUris(BlockingHttpClient client) {
        fetchHttpServerRequestMetrics(client)["availableTags"].find { it.tag == "uri" }?.values
    }

    private static Map<String, Object> fetchHttpServerRequestMetrics(BlockingHttpClient client) {
        client.retrieve(HttpRequest.GET("/metrics/http.server.requests"), Argument.mapOf(String, Object));
    }

    @Requires(property = "spec.name", value = "RoutePathVariableNotFoundMetricsSpec")
    @Controller
    static class BookController {
        @Get("/books/{id}")
        @Status(HttpStatus.OK)
        void index(@PathVariable String id) {
        }
    }
}

