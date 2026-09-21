package io.micronaut.http.client.netty

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.CLIENT_ALL
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.CLIENT_TAG
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.CONNECTIONS
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.CONNECTIONS_CREATED
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.CONNECTIONS_CREATE_TIME
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.REQUESTS
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.STATE_ACTIVE
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.STATE_OPEN
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.STATE_PENDING
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.STATE_TAG

class HttpClientPoolMetricsSpec extends Specification {

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(HttpClientPoolMetricsClientBinder).isPresent() == result
        context.findBean(HttpClientPoolMetricsCustomizerBinder).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                                                        | setting | result
        MICRONAUT_METRICS_ENABLED                                  | true    | false
        MICRONAUT_METRICS_ENABLED                                  | false   | false
        MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled"     | true    | true
        MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled"     | "yes"   | true
        MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled"     | false   | false
    }

    @Unroll
    void "test http client pool metrics meters are present for #description"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, properties + [
                (MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled"): true,
                "spec.name": getClass().getSimpleName()
        ])
        MeterRegistry registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        def client = embeddedServer.applicationContext.getBean(clientType)
        embeddedServer.applicationContext.getBean(DefaultHttpClient)

        then:
        client.root() == "root"

        and:
        registry.get(CONNECTIONS_CREATED).tag(CLIENT_TAG, CLIENT_ALL).counter().count() >= 1
        registry.get(CONNECTIONS_CREATE_TIME).tag(CLIENT_TAG, CLIENT_ALL).timer().count() >= 1
        registry.get(CONNECTIONS).tags(CLIENT_TAG, CLIENT_ALL, STATE_TAG, STATE_OPEN).gauge().value() >= 1
        registry.get(CONNECTIONS).tags(CLIENT_TAG, CLIENT_ALL, STATE_TAG, STATE_PENDING).gauge().value() == 0
        registry.get(REQUESTS).tags(CLIENT_TAG, "Primary", STATE_TAG, STATE_ACTIVE).gauge().value() == 0

        cleanup:
        embeddedServer.close()

        where:
        description | clientType       | properties
        "http/1.1"  | DummyClient      | [:]
        "http/2"    | Http2DummyClient | [
                "micronaut.server.http-version"                                : "HTTP_2_0",
                "micronaut.server.ssl.build-self-signed"                       : true,
                "micronaut.server.ssl.enabled"                                 : true,
                "micronaut.server.ssl.port"                                    : 0,
                "micronaut.http.client.ssl.insecure-trust-all-certificates"    : true
        ]
    }

    void "test websocket clients do not register pool connection attempts"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                (MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled"): true,
                "spec.name": getClass().getSimpleName()
        ])
        MeterRegistry registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        WebSocketDummyClient client = createWebSocketClient(embeddedServer.applicationContext, embeddedServer.port, "Travolta")

        then:
        client != null
        registry.get(CONNECTIONS_CREATED).tag(CLIENT_TAG, CLIENT_ALL).counter().count() == 0
        registry.get(CONNECTIONS).tags(CLIENT_TAG, CLIENT_ALL, STATE_TAG, STATE_OPEN).gauge().value() == 0
        registry.get(CONNECTIONS).tags(CLIENT_TAG, CLIENT_ALL, STATE_TAG, STATE_PENDING).gauge().value() == 0

        cleanup:
        client?.close()
        embeddedServer?.close()
    }

    private static WebSocketDummyClient createWebSocketClient(ApplicationContext context, int port, String username) {
        WebSocketClient webSocketClient = context.getBean(WebSocketClient)
        URI uri = UriBuilder.of("ws://localhost")
                .port(port)
                .path("pool-metrics-ws")
                .path("{username}")
                .expand(CollectionUtils.mapOf("username", username))
        Flux.from(webSocketClient.connect(WebSocketDummyClient, uri)).blockFirst()
    }

    @Requires(property = "spec.name", value = "HttpClientPoolMetricsSpec")
    @Client("/pool-metrics")
    private static interface DummyClient {
        @Get
        String root()
    }

    @Requires(property = "spec.name", value = "HttpClientPoolMetricsSpec")
    @Client(value = "/pool-metrics", alpnModes = HttpVersionSelection.ALPN_HTTP_2)
    private static interface Http2DummyClient {
        @Get
        String root()
    }

    @Requires(property = "spec.name", value = "HttpClientPoolMetricsSpec")
    @ClientWebSocket
    private static abstract class WebSocketDummyClient implements AutoCloseable {
        @OnMessage
        void onMessage(String message) {
        }
    }

    @Requires(property = "spec.name", value = "HttpClientPoolMetricsSpec")
    @Controller("/pool-metrics")
    private static class DummyController {
        @Get
        String root() {
            "root"
        }
    }

    @Requires(property = "spec.name", value = "HttpClientPoolMetricsSpec")
    @ServerWebSocket("/pool-metrics-ws/{username}")
    private static class DummyWebSocket {
        private final WebSocketBroadcaster broadcaster

        DummyWebSocket(WebSocketBroadcaster broadcaster) {
            this.broadcaster = broadcaster
        }

        @OnOpen
        Publisher<String> onOpen(String username, WebSocketSession session) {
            broadcaster.broadcast("Joined $username")
        }

        @OnMessage
        Publisher<String> onMessage(String username, String message, WebSocketSession session) {
            broadcaster.broadcast("[$username] $message")
        }

        @OnClose
        Publisher<String> onClose(String username, WebSocketSession session) {
            broadcaster.broadcast("Leaving $username")
        }
    }
}
