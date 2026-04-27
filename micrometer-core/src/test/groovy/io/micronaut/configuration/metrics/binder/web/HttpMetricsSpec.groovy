package io.micronaut.configuration.metrics.binder.web

import groovy.transform.InheritConstructors
import io.micrometer.common.lang.NonNull
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.distribution.HistogramSnapshot
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micrometer.core.instrument.Tags
import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.ThreadPropagatedContextElement
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.*
import org.reactivestreams.Publisher
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.slf4j.MDC
import reactor.core.publisher.Flux
import spock.lang.PendingFeature
import spock.lang.Specification

import jakarta.inject.Singleton
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

    void "test rxjava3 server metrics keep custom meter filter tags"() {
        when:
        EmbeddedServer embeddedServer
        embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': getClass().getSimpleName()])
        def context = embeddedServer.applicationContext
        TestClient client = context.getBean(TestClient)

        then:
        client.rxjava3() == 'rxjava3'

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)

        then:
        registry.get('custom.metric').tag('traceId', 'rxjava3').counter().count() == 1
        registry.get(HttpServerMeterConfig.REQUESTS_METRIC)
            .tags('uri', '/test-http-metrics/rxjava3', 'traceId', 'rxjava3')
            .timer()
            .count() == 1

        cleanup:
        embeddedServer?.close()
    }

    @PendingFeature
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

        @Get("/test-http-metrics-not-found")
        HttpResponse notFound()

        @Get("/test-http-metrics/rxjava3")
        String rxjava3()
    }

    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    @Controller
    static class TestController {

        private final MeterRegistry meterRegistry

        TestController(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry
        }

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

        @Get("/test-http-metrics/rxjava3")
        Single<String> rxjava3() {
            return Single.fromCallable(() -> {
                meterRegistry.counter('custom.metric').increment()
                return 'rxjava3'
            }).subscribeOn(Schedulers.io())
        }

        @Error(exception = MyException)
        HttpResponse<?> myExceptionHandler() {
            return HttpResponse.badRequest()
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    static class MeterFilterFactory {

        @Singleton
        MeterFilter traceIdMeterFilter() {
            return new MeterFilter() {
                @Override
                io.micrometer.core.instrument.Meter.Id map(io.micrometer.core.instrument.Meter.Id id) {
                    String traceId = MDC.get('traceId')
                    if (traceId == null) {
                        return id
                    }
                    Iterable<Tag> tagsWithoutTraceId = id.getTagsAsIterable().findAll { Tag tag -> tag.key != 'traceId' }
                    return id.replaceTags(Tags.concat([Tag.of('traceId', traceId)], tagsWithoutTraceId))
                }
            }
        }
    }

    @Singleton
    @ServerFilter("/test-http-metrics/rxjava3")
    @Requires(property = "spec.name", value = "HttpMetricsSpec")
    static class TraceIdFilter {

        @RequestFilter
        void request(MutablePropagatedContext mutablePropagatedContext) {
            MDC.put('traceId', 'rxjava3')
            mutablePropagatedContext.add(new MdcPropagationContext(MDC.getCopyOfContextMap()))
            MDC.clear()
        }
    }

    static record MdcPropagationContext(Map<String, String> contextMap) implements ThreadPropagatedContextElement<Map<String, String>> {

        @Override
        Map<String, String> updateThreadContext() {
            Map<String, String> oldContextMap = MDC.getCopyOfContextMap()
            if (contextMap == null || contextMap.isEmpty()) {
                MDC.clear()
            } else {
                MDC.setContextMap(contextMap)
            }
            return oldContextMap
        }

        @Override
        void restoreThreadContext(Map<String, String> oldState) {
            if (oldState == null || oldState.isEmpty()) {
                MDC.clear()
            } else {
                MDC.setContextMap(oldState)
            }
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
