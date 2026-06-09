package io.micronaut.configuration.metrics.binder.web

import groovy.transform.InheritConstructors
import io.micrometer.common.lang.NonNull
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.distribution.HistogramSnapshot
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig
import io.micronaut.configuration.metrics.binder.web.config.HttpMetricsConfig
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterPhase
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import jakarta.inject.Provider
import jakarta.validation.constraints.NotBlank

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.http.HttpStatus.CONFLICT
import static io.micronaut.http.HttpStatus.NOT_FOUND

class HttpMetricsSpec extends Specification {

    void "test client / server metrics with #cfg #setting"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [(cfg): setting, 'spec.name': getClass().getSimpleName()])
        def context = embeddedServer.applicationContext
        TestClient client = context.getBean(TestClient)

        then:
        client.index() == 'ok'

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)

        Timer serverTimer = registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics').timer()
        Timer clientTimer = registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics').timer()
        HistogramSnapshot serverSnapshot = serverTimer.takeSnapshot()
        HistogramSnapshot clientSnapshot = clientTimer.takeSnapshot()
        DistributionStatisticConfig serverDistributionConfig = serverTimer.getMetaPropertyValues().find { it.name.equals('distributionStatisticConfig') }.value as DistributionStatisticConfig
        DistributionStatisticConfig clientDistributionConfig = clientTimer.getMetaPropertyValues().find { it.name.equals('distributionStatisticConfig') }.value as DistributionStatisticConfig

        then:
        serverTimer.count() == 1
        clientTimer.count() == 1

        serverSnapshot.percentileValues().length == serverPercentilesCount
        clientSnapshot.percentileValues().length == clientPercentilesCount

        serverDistributionConfig.percentileHistogram == serverHistogram
        clientDistributionConfig.percentileHistogram == clientHistogram

        serverDistributionConfig.getServiceLevelObjectiveBoundaries()?.length == serverSlosCount
        clientDistributionConfig.getServiceLevelObjectiveBoundaries()?.length == clientSlosCount

        serverDistributionConfig.minimumExpectedValueAsDouble == serverMin
        serverDistributionConfig.maximumExpectedValueAsDouble == serverMax

        clientDistributionConfig.minimumExpectedValueAsDouble == clientMin
        clientDistributionConfig.maximumExpectedValueAsDouble == clientMax

        when: "A request is sent to the root route"

        then:
        client.root() == 'root'
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags('uri', 'root').timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', 'root').timer().count() == 1

        when: "A request is sent with a uri template"
        String result = client.template("foo")

        then:
        result == 'ok foo'
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics/{id}').timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics/{id}').timer().count() == 1
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags('serviceId', 'embedded-server').timer().count() == 1

        when:
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics/foo').timer()

        then:
        thrown(MeterNotFoundException)

        when: "A request is made that returns an error response"
        client.error()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "409").timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "409").timer().count() == 1

        when: "A request is made that throws an exception"
        client.throwable()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "500").timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "500").timer().count() == 1

        when: "A request is made that throws an exception that is handled"
        client.exceptionHandling()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "400", "uri", "/test-http-metrics/exception-handling").timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "400", "uri", "/test-http-metrics/exception-handling").timer().count() == 1

        when: "A request is made that does not match a route"
        HttpResponse response = client.notFound()

        then:
        noExceptionThrown()
        response.status() == NOT_FOUND
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "404").timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "404").timer().count() == 1

        cleanup:
        embeddedServer.close()

        where:
        cfg                                                   | setting       | serverPercentilesCount | clientPercentilesCount | serverSlosCount | clientSlosCount | serverHistogram | clientHistogram | serverMin | serverMax | clientMin | clientMax
        // Server
        MICRONAUT_METRICS_BINDERS + ".web.server.percentiles" | "0.95,0.99"   | 2                      | 0                      | 0               | null            | false           | null            | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.server.histogram"   | "true"        | 0                      | 0                      | 0               | null            | true            | null            | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.server.histogram"   | "false"       | 0                      | 0                      | 0               | null            | false           | null            | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.server.min"         | 0.1           | 0                      | 0                      | 0               | null            | false           | null            | 1.0E8     | 3.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.server.max"         | 60            | 0                      | 0                      | 0               | null            | false           | null            | 1000000d  | 6.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.server.slos"        | "0.1,0.2,0.5" | 0                      | 0                      | 3               | null            | false           | null            | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
        // Client
        MICRONAUT_METRICS_BINDERS + ".web.client.percentiles" | "0.95,0.99"   | 0                      | 2                      | null            | 0               | null            | false           | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.client.histogram"   | "true"        | 0                      | 0                      | null            | 0               | null            | true            | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.client.histogram"   | "false"       | 0                      | 0                      | null            | 0               | null            | false           | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.client.min"         | 0.1           | 0                      | 0                      | null            | 0               | null            | false           | 1000000d  | 3.0E10    | 1.0E8     | 3.0E10
        MICRONAUT_METRICS_BINDERS + ".web.client.max"         | 60            | 0                      | 0                      | null            | 0               | null            | false           | 1000000d  | 3.0E10    | 1000000d  | 6.0E10
        MICRONAUT_METRICS_BINDERS + ".web.client.slos"        | "0.1,0.2,0.5" | 0                      | 0                      | null            | 3               | null            | false           | 1000000d  | 3.0E10    | 1000000d  | 3.0E10
    }

    void "test client / server metrics ignored uris for client errors"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.metrics.binders.web.client-errors-uris.enabled': false,
                'spec.name': getClass().getSimpleName()
        ])
        def context = embeddedServer.getApplicationContext()
        TestClient client = context.getBean(TestClient)

        then:
        client.index() == 'ok'

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)

        Timer serverTimer = registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics').timer()
        Timer clientTimer = registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics').timer()

        then:
        serverTimer.count() == 1
        clientTimer.count() == 1

        when:
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics/foo').timer()

        then:
        thrown(MeterNotFoundException)

        when: "A request is made that returns an error response"
        client.error()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "409",).timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "409").timer().count() == 1

        when: "A request is made that throws an exception"
        client.throwable()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "500").timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "500").timer().count() == 1

        when: "A request is made that throws an exception that is handled"
        client.exceptionHandling()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "400", "uri", "/test-http-metrics/exception-handling").timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "400", "uri", "BAD_REQUEST").timer().count() == 1

        when: "A request is made that does not match a route"
        HttpResponse response = client.notFound()

        then:
        noExceptionThrown()
        response.status() == NOT_FOUND
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags("status", "404").timer().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "404").timer().count() == 1

        cleanup:
        embeddedServer.close()
    }

    void "test server metrics ignore preflight requests"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.cors.enabled': true,
                'micronaut.server.cors.configurations.web.allowed-origins': ['https://example.com'],
                'spec.name': getClass().getSimpleName()
        ])
        def context = embeddedServer.applicationContext
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.OPTIONS('/test-http-metrics/foo')
                .header(HttpHeaders.ORIGIN, 'https://example.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET'))

        then:
        response.code() >= 200 && response.code() < 300

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)
        List<String> optionUris = registry.meters
                .findAll { it.id.name == HttpServerMeterConfig.REQUESTS_METRIC && it.id.getTag('method') == 'OPTIONS' }
                .collect { it.id.getTag('uri') }

        then:
        optionUris.isEmpty()

        cleanup:
        client.close()
    }

    void "test server metrics record security-style short-circuit 401 and 403 responses"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().getSimpleName()
        ])
        def context = embeddedServer.applicationContext
        TestClient client = context.getBean(TestClient)
        MeterRegistry registry = context.getBean(MeterRegistry)

        when:
        client.unauthorized()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "401", "uri", "/test-http-metrics/unauthorized").timer().count() == 1

        when:
        client.forbidden()

        then:
        thrown(HttpClientResponseException)
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "403", "uri", "/test-http-metrics/forbidden").timer().count() == 1

        cleanup:
        embeddedServer.close()
    }

    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting, 'spec.name': getClass().getSimpleName()])

        then:
        context.findBean(ClientMetricsFilter).isPresent() == setting
        context.findBean(ServerMetricsFilter).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                           | setting
        MICRONAUT_METRICS_ENABLED     | true
        MICRONAUT_METRICS_ENABLED     | false
        (WebMetricsPublisher.ENABLED) | true
        (WebMetricsPublisher.ENABLED) | false
    }

    void "deprecated server request filter uses configured client error URI reporting"() {
        given:
        MeterRegistry registry = new SimpleMeterRegistry()
        def filter = new ServerRequestMeterRegistryFilter(
                { registry } as Provider<MeterRegistry>,
                new HttpMetricsConfig.ClientErrorsUrisConfig(reportClientErrorUris)
        )
        def request = HttpRequest.GET("/test-http-metrics/exception-handling")
                .setAttribute(HttpAttributes.URI_TEMPLATE, "/test-http-metrics/exception-handling")
        ServerFilterChain chain = Mock()

        when:
        Flux.from(filter.doFilter(request, chain)).blockLast()

        then:
        1 * chain.proceed(request) >> Flux.just(HttpResponse.status(CONFLICT))
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags("status", "409", "uri", expectedUri).timer().count() == 1

        where:
        reportClientErrorUris | expectedUri
        true                  | "/test-http-metrics/exception-handling"
        false                 | "BAD_REQUEST"
    }

    void "deprecated server request filter reports client error URIs by default"() {
        given:
        MeterRegistry registry = new SimpleMeterRegistry()
        def filter = new ServerRequestMeterRegistryFilter({ registry } as Provider<MeterRegistry>)
        def request = HttpRequest.GET("/test-http-metrics/exception-handling")
                .setAttribute(HttpAttributes.URI_TEMPLATE, "/test-http-metrics/exception-handling")
        ServerFilterChain chain = Mock()

        when:
        Flux.from(filter.doFilter(request, chain)).blockLast()

        then:
        1 * chain.proceed(request) >> Flux.just(HttpResponse.status(CONFLICT))
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC)
                .tags("status", "409", "uri", "/test-http-metrics/exception-handling")
                .timer()
                .count() == 1
    }

    void "test server metrics filter uses metrics phase order"() {
        when:
        ApplicationContext context = ApplicationContext.run(['spec.name': getClass().getSimpleName()])

        then:
        context.getBean(ServerMetricsFilter).getOrder() == ServerFilterPhase.METRICS.order()

        cleanup:
        context.close()
    }

    void "test websocket"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                (MICRONAUT_METRICS_ENABLED): true,
                "spec.name": getClass().getSimpleName(),
        ])
        MeterRegistry registry = embeddedServer.getApplicationContext().getBean(MeterRegistry)
        createWebSocketClient(embeddedServer.getApplicationContext(), embeddedServer.getPort(), "Travolta")

        then:
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/ws/{username}').timer()

        cleanup:
        embeddedServer.close()
    }

    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    @ClientWebSocket
    static abstract class TestWebSocketClient implements AutoCloseable {
        abstract void send(@NonNull @NotBlank String message);

        @OnMessage
        void onMessage(String message) {}
    }

    private static TestWebSocketClient createWebSocketClient(ApplicationContext context, int port, String username) {
        WebSocketClient webSocketClient = context.getBean(WebSocketClient.class)
        URI uri = UriBuilder.of("ws://localhost")
                .port(port)
                .path("ws")
                .path("{username}")
                .expand(CollectionUtils.mapOf("username", username))
        Publisher<TestWebSocketClient> client = webSocketClient.connect(TestWebSocketClient.class, uri)
        return Flux.from(client).blockFirst()
    }

    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    @Client('/')
    static interface TestClient {
        @Get
        String root()

        @Get('/test-http-metrics')
        String index()

        @Get("/test-http-metrics/{id}")
        String template(String id)

        @Get("/test-http-metrics/error")
        HttpResponse error()

        @Get("/test-http-metrics/throwable")
        HttpResponse throwable()

        @Get("/test-http-metrics/exception-handling")
        HttpResponse exceptionHandling()

        @Get("/test-http-metrics/unauthorized")
        HttpResponse unauthorized()

        @Get("/test-http-metrics/forbidden")
        HttpResponse forbidden()

        @Get("/test-http-metrics-not-found")
        HttpResponse notFound()
    }

    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    @Controller
    static class TestController {
        @Get
        String root() { "root" }

        @Get('/test-http-metrics')
        String index() { "ok" }

        @Get("/test-http-metrics/{id}")
        String template(String id) { "ok " + id }

        @Get("/test-http-metrics/error")
        HttpResponse error() {
            HttpResponse.status(CONFLICT)
        }

        @Get("/test-http-metrics/throwable")
        HttpResponse throwable() {
            throw new CustomRuntimeException()
        }

        @Get("/test-http-metrics/exception-handling")
        HttpResponse exceptionHandling() {
            throw new MyException("my custom exception")
        }

        @Get("/test-http-metrics/unauthorized")
        String unauthorized() { "unauthorized" }

        @Get("/test-http-metrics/forbidden")
        String forbidden() { "forbidden" }

        @Error(exception = MyException)
        HttpResponse<?> myExceptionHandler() {
            return HttpResponse.badRequest()
        }
    }

    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    @Filter("/test-http-metrics/**")
    static class SecurityShortCircuitFilter implements HttpServerFilter, Ordered {

        @Override
        int getOrder() {
            return ServerFilterPhase.SECURITY.order()
        }

        @Override
        Publisher<io.micronaut.http.MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            if (request.path == "/test-http-metrics/unauthorized") {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "unauthorized")
            }
            if (request.path == "/test-http-metrics/forbidden") {
                throw new HttpStatusException(HttpStatus.FORBIDDEN, "forbidden")
            }
            return chain.proceed(request)
        }
    }

    static class CustomRuntimeException extends RuntimeException {

    }

    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    @ServerWebSocket("/ws/{username}")
    static class TestWSController {

        private final WebSocketBroadcaster broadcaster

        TestWSController(WebSocketBroadcaster broadcaster) {
            this.broadcaster = broadcaster
        }

        @OnOpen
        Publisher<String> onOpen(String username, WebSocketSession session) {
            return broadcaster.broadcast(String.format("Joined %s!", username))
        }

        @OnMessage
        Publisher<String> onMessage(
                String username,
                String message,
                WebSocketSession session) {
            return broadcaster.broadcast(String.format("[%s] %s", username, message))
        }

        @OnClose
        Publisher<String> onClose(
                String username,
                WebSocketSession session) {
            return broadcaster.broadcast(String.format("Leaving %s!", username))
        }

    }

    @InheritConstructors
    static class MyException extends RuntimeException {
    }
}
