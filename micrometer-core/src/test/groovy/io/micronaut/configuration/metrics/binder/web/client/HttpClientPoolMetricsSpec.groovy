package io.micronaut.configuration.metrics.binder.web.client

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
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
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.CONNECTIONS
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.CONNECTIONS_CREATED
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.CONNECTIONS_CREATE_TIME
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.CONNECTIONS_FAILED
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.ENABLED
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.HTTP_1_1
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.HTTP_2
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.PROTOCOL_TAG
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.REQUESTS
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.STATE_ACTIVE
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.STATE_OPEN
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.STATE_PENDING
import static io.micronaut.configuration.metrics.binder.web.client.HttpClientPoolMetrics.STATE_TAG
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class HttpClientPoolMetricsSpec extends Specification {

    private static final String SPEC_NAME = 'HttpClientPoolMetricsSpec'

    PollingConditions conditions = new PollingConditions(timeout: 10, delay: 0.1)

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(HttpClientPoolMetrics).isPresent() == result
        context.findBean(HttpClientPoolMetricsBinder).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | true    | false
        MICRONAUT_METRICS_ENABLED | false   | false
        ENABLED                   | true    | true
        ENABLED                   | "yes"   | true
        ENABLED                   | false   | false
    }

    @Unroll
    void "test http client pool metrics for #protocol"() {
        given:
        EmbeddedServer embeddedServer = startServer(properties)
        MeterRegistry registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        def client = embeddedServer.applicationContext.getBean(clientType)

        expect:
        client.root() == "root"
        registry.get(CONNECTIONS_CREATED).tag(PROTOCOL_TAG, protocol).counter().count() >= 1
        registry.get(CONNECTIONS_CREATE_TIME).tag(PROTOCOL_TAG, protocol).timer().count() >= 1
        gauge(registry, CONNECTIONS, STATE_OPEN) >= 1
        gauge(registry, CONNECTIONS, STATE_PENDING) == 0
        registry.get(CONNECTIONS_FAILED).counter().count() == 0
        conditions.eventually {
            assert gauge(registry, REQUESTS, STATE_ACTIVE) == 0
        }

        cleanup:
        embeddedServer.close()

        where:
        protocol | clientType       | properties
        HTTP_1_1 | DummyClient      | [:]
        HTTP_2   | Http2DummyClient | [
                "micronaut.server.http-version"                            : "HTTP_2_0",
                "micronaut.server.ssl.build-self-signed"                   : true,
                "micronaut.server.ssl.enabled"                             : true,
                "micronaut.server.ssl.port"                                : 0,
                "micronaut.http.client.ssl.insecure-trust-all-certificates": true
        ]
    }

    @Unroll
    void "test a running #protocol request is active until it completes"() {
        given:
        EmbeddedServer embeddedServer = startServer(properties)
        MeterRegistry registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        DummyController controller = embeddedServer.applicationContext.getBean(DummyController)
        def client = embeddedServer.applicationContext.getBean(clientType)

        when:
        def response = Mono.from(client.slow()).toFuture()

        then:
        conditions.eventually {
            assert gauge(registry, REQUESTS, STATE_ACTIVE) == 1
        }

        when:
        controller.release.countDown()

        then:
        response.get(10, TimeUnit.SECONDS) == "slow"
        conditions.eventually {
            assert gauge(registry, REQUESTS, STATE_ACTIVE) == 0
        }

        cleanup:
        embeddedServer.close()

        where:
        protocol | clientType       | properties
        HTTP_1_1 | DummyClient      | [:]
        HTTP_2   | Http2DummyClient | [
                "micronaut.server.http-version"                            : "HTTP_2_0",
                "micronaut.server.ssl.build-self-signed"                   : true,
                "micronaut.server.ssl.enabled"                             : true,
                "micronaut.server.ssl.port"                                : 0,
                "micronaut.http.client.ssl.insecure-trust-all-certificates": true
        ]
    }

    void "test the connections of evicted pools and of a closed client are counted down without leaving meters behind"() {
        given: "idle connections close quickly, and the client then evicts their per-host pools"
        EmbeddedServer embeddedServer = startServer(["micronaut.http.client.connection-pool-idle-timeout": "200ms"])
        ApplicationContext context = embeddedServer.applicationContext
        MeterRegistry registry = context.getBean(MeterRegistry)
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)
        List<String> hosts = ["localhost", "127.0.0.1"]
        assert client.toBlocking().retrieve("http://localhost:${embeddedServer.port}/pool-metrics") == "root"
        Set<Meter.Id> meters = poolMeters(registry)

        when: "each host gets its own pool"
        for (String host : hosts) {
            assert client.toBlocking().retrieve("http://$host:${embeddedServer.port}/pool-metrics") == "root"
        }

        then:
        meters.size() == 10
        gauge(registry, CONNECTIONS, STATE_OPEN) == hosts.size()

        and: "the idle connections close and their pools are evicted"
        conditions.eventually {
            assert gauge(registry, CONNECTIONS, STATE_OPEN) == 0
        }
        gauge(registry, CONNECTIONS, STATE_PENDING) == 0
        gauge(registry, REQUESTS, STATE_ACTIVE) == 0
        poolMeters(registry) == meters

        when: "new pools are created for the same hosts"
        for (String host : hosts) {
            assert client.toBlocking().retrieve("http://$host:${embeddedServer.port}/pool-metrics") == "root"
        }

        then:
        gauge(registry, CONNECTIONS, STATE_OPEN) == hosts.size()

        when:
        client.close()

        then:
        conditions.eventually {
            assert gauge(registry, CONNECTIONS, STATE_OPEN) == 0
        }
        gauge(registry, CONNECTIONS, STATE_PENDING) == 0
        gauge(registry, REQUESTS, STATE_ACTIVE) == 0
        registry.get(CONNECTIONS_FAILED).counter().count() == 0
        poolMeters(registry) == meters

        cleanup:
        embeddedServer.close()
    }

    void "test a connection that fails to connect is counted as failed"() {
        given:
        EmbeddedServer embeddedServer = startServer()
        ApplicationContext context = embeddedServer.applicationContext
        MeterRegistry registry = context.getBean(MeterRegistry)
        int closedPort = new ServerSocket(0).withCloseable { it.localPort }
        HttpClient client = context.createBean(HttpClient, new URL("http://localhost:$closedPort"))

        when:
        client.toBlocking().retrieve("/pool-metrics")

        then:
        thrown(HttpClientException)
        conditions.eventually {
            assert registry.get(CONNECTIONS_FAILED).counter().count() >= 1
            assert gauge(registry, CONNECTIONS, STATE_PENDING) == 0
        }
        gauge(registry, CONNECTIONS, STATE_OPEN) == 0
        registry.get(CONNECTIONS_CREATED).tag(PROTOCOL_TAG, HTTP_1_1).counter().count() == 0

        cleanup:
        client?.close()
        embeddedServer.close()
    }

    void "test websocket clients do not register pool connections"() {
        given:
        EmbeddedServer embeddedServer = startServer()
        MeterRegistry registry = embeddedServer.applicationContext.getBean(MeterRegistry)

        when:
        WebSocketDummyClient client = createWebSocketClient(embeddedServer.applicationContext, embeddedServer.port, "Travolta")

        then:
        client != null
        registry.get(CONNECTIONS_CREATED).tag(PROTOCOL_TAG, HTTP_1_1).counter().count() == 0
        gauge(registry, CONNECTIONS, STATE_OPEN) == 0
        gauge(registry, CONNECTIONS, STATE_PENDING) == 0
        registry.get(CONNECTIONS_FAILED).counter().count() == 0

        when:
        client.close()

        then:
        conditions.eventually {
            assert gauge(registry, CONNECTIONS, STATE_PENDING) == 0
        }
        registry.get(CONNECTIONS_FAILED).counter().count() == 0

        cleanup:
        embeddedServer.close()
    }

    private static EmbeddedServer startServer(Map<String, Object> properties = [:]) {
        ApplicationContext.run(EmbeddedServer, properties + [
                (ENABLED)  : true,
                "spec.name": SPEC_NAME
        ])
    }

    private static double gauge(MeterRegistry registry, String name, String state) {
        registry.get(name).tag(STATE_TAG, state).gauge().value()
    }

    private static Set<Meter.Id> poolMeters(MeterRegistry registry) {
        registry.meters*.id.findAll { it.name.startsWith("http.client.pool.") }.toSet()
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

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Client("/pool-metrics")
    static interface DummyClient {
        @Get
        String root()

        @Get("/slow")
        Publisher<String> slow()
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Client(value = "/pool-metrics", alpnModes = HttpVersionSelection.ALPN_HTTP_2)
    static interface Http2DummyClient {
        @Get
        String root()

        @Get("/slow")
        Publisher<String> slow()
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ClientWebSocket
    static abstract class WebSocketDummyClient implements AutoCloseable {
        @OnMessage
        void onMessage(String message) {
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/pool-metrics")
    static class DummyController {
        final CountDownLatch release = new CountDownLatch(1)

        @Get
        String root() {
            "root"
        }

        @Get("/slow")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String slow() {
            release.await(10, TimeUnit.SECONDS)
            "slow"
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/pool-metrics-ws/{username}")
    static class DummyWebSocket {
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
