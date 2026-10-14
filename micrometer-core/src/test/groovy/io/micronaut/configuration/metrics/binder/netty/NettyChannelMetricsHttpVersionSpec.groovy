package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS

class NettyChannelMetricsHttpVersionSpec extends Specification {

    private static final String SPEC_NAME = 'NettyChannelMetricsHttpVersionSpec'

    PollingConditions conditions = new PollingConditions(timeout: 10)

    @AutoCleanup
    ApplicationContext context

    def nettyLogs = Class.forName("ch.qos.logback.core.read.ListAppender").getDeclaredConstructor().newInstance()

    def setup() {
        nettyLogs.start()
        org.slf4j.LoggerFactory.getLogger("io.netty").addAppender(nettyLogs)
    }

    def cleanup() {
        org.slf4j.LoggerFactory.getLogger("io.netty").detachAppender(nettyLogs)
    }

    private List<String> nettyErrors() {
        nettyLogs.list.collect { event -> event.formattedMessage + " " + (event.throwableProxy?.message ?: "") }
    }

    @Unroll
    void "HTTP/1.1 connections are accounted once each with #description"() {
        given:
        EmbeddedServer server = start(config)
        MeterRegistry registry = context.getBean(MeterRegistry)
        int connections = 3

        when: 'each request uses a new connection that the server closes'
        connections.times {
            assert http1Request(server) == 'pong'
        }

        then:
        conditions.eventually {
            assert activeChannels(registry) == 0
            assert channelCount(registry) == connections
            assert activeChannelTimer(registry) == connections
        }
        bytesRead(registry) > 0
        bytesWritten(registry) > 0

        where:
        description               | config
        'HTTP/1.1'                | [:]
        'h2c support (fallback)'  | ['micronaut.server.http-version': '2.0']
    }

    @Unroll
    void "HTTP/2 connections are accounted with #description"() {
        given:
        EmbeddedServer server = start(config)
        MeterRegistry registry = context.getBean(MeterRegistry)
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .sslContext(trustAll())
                .build()

        when: 'several requests share one connection'
        List<HttpResponse<String>> responses = (1..5).collect {
            client.send(HttpRequest.newBuilder(server.URI.resolve('/channel-metrics/ping')).GET().build(), HttpResponse.BodyHandlers.ofString())
        }

        then:
        responses.every { it.statusCode() == 200 && it.body() == 'pong' }
        responses.last().version() == HttpClient.Version.HTTP_2
        !nettyErrors().any { it.contains("Duplicate handler name") }
        conditions.eventually {
            assert channelCount(registry) == 1
            assert activeChannels(registry) == 1
            assert bytesRead(registry) > 0
            assert bytesWritten(registry) > 0
        }

        when:
        client.close()

        then:
        conditions.eventually {
            assert activeChannels(registry) == 0
            assert activeChannelTimer(registry) == 1
        }
        channelCount(registry) == 1

        where:
        description                       | config
        'TLS and ALPN'                    | ssl()
        'TLS, ALPN and legacy multiplex'  | ssl() + ['micronaut.server.netty.legacy-multiplex-handlers': true]
        'h2c upgrade'                     | ['micronaut.server.http-version': '2.0']
        'h2c upgrade and legacy multiplex'| ['micronaut.server.http-version': '2.0', 'micronaut.server.netty.legacy-multiplex-handlers': true]
    }

    private EmbeddedServer start(Map<String, Object> config) {
        context = ApplicationContext.run([
                'spec.name'                                           : SPEC_NAME,
                (MICRONAUT_METRICS_BINDERS + '.netty.channels.enabled'): true,
                (MICRONAUT_METRICS_BINDERS + '.jvm.enabled')           : false,
                'micronaut.server.port'                               : -1,
        ] + config)
        return context.getBean(EmbeddedServer).start()
    }

    private static Map<String, Object> ssl() {
        [
                'micronaut.server.http-version'         : '2.0',
                'micronaut.server.ssl.enabled'          : true,
                'micronaut.server.ssl.build-self-signed': true,
                'micronaut.server.ssl.port'             : -1,
        ]
    }

    private static String http1Request(EmbeddedServer server) {
        new Socket(server.host, server.port).withCloseable { socket ->
            socket.outputStream.write(("GET /channel-metrics/ping HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            String response = new String(socket.inputStream.readAllBytes(), StandardCharsets.US_ASCII)
            assert response.startsWith('HTTP/1.1 200')
            return response.substring(response.indexOf('\r\n\r\n') + 4)
        }
    }

    private static SSLContext trustAll() {
        SSLContext sslContext = SSLContext.getInstance('TLS')
        sslContext.init(null, [new X509TrustManager() {
            @Override
            void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0]
            }
        }] as TrustManager[], new SecureRandom())
        return sslContext
    }

    private static double activeChannels(MeterRegistry registry) {
        registry.get('netty.channel.count.active').gauge().value()
    }

    private static double channelCount(MeterRegistry registry) {
        registry.get('netty.channel.count').tags('channel', 'count').counter().count()
    }

    private static long activeChannelTimer(MeterRegistry registry) {
        registry.get('netty.channel.time').timer().count()
    }

    private static double bytesRead(MeterRegistry registry) {
        registry.get('netty.channel.byte').tags('byte', 'read').counter().count()
    }

    private static double bytesWritten(MeterRegistry registry) {
        registry.get('netty.channel.byte').tags('byte', 'written').counter().count()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/channel-metrics')
    static class PingController {
        @Get(uri = '/ping', produces = 'text/plain')
        String ping() {
            'pong'
        }
    }
}
