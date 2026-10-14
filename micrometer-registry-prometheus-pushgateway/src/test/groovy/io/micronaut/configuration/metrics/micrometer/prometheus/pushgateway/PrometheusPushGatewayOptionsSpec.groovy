package io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway

import com.sun.net.httpserver.HttpServer
import io.micronaut.context.ApplicationContext
import io.prometheus.metrics.exporter.pushgateway.PushGateway
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

import static io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway.PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_CONFIG

class PrometheusPushGatewayOptionsSpec extends Specification {

    List<Map<String, String>> requests = new CopyOnWriteArrayList<>()

    HttpServer pushGateway = startPushGateway()

    def cleanup() {
        pushGateway.stop(0)
    }

    void "metrics are #description on shutdown when shutdown-operation is #operation"() {
        given:
        ApplicationContext context = ApplicationContext.run(config((PROMETHEUS_PUSHGATEWAY_CONFIG + '.shutdown-operation'): operation))

        expect:
        context.getBean(PrometheusPushGatewayConfig).shutdownOperation.name().equalsIgnoreCase(operation)
        requests.isEmpty()

        when:
        context.close()

        then:
        requests*.method == methods
        requests.every { it.path == '/metrics/job/test' }

        where:
        operation | methods    | description
        'none'    | []         | 'left'
        'push'    | ['PUT']    | 'pushed'
        'delete'  | ['DELETE'] | 'deleted'
    }

    void "nothing is done on shutdown by default"() {
        given:
        ApplicationContext context = ApplicationContext.run(config([:]))

        when:
        context.close()

        then:
        requests.isEmpty()
    }

    void "the metrics are pushed with a bearer token in the #format format"() {
        given:
        Map<String, Object> properties = [(PROMETHEUS_PUSHGATEWAY_CONFIG + '.bearer-token'): 'secret-token']
        if (format) {
            properties[PROMETHEUS_PUSHGATEWAY_CONFIG + '.format'] = format
        }
        ApplicationContext context = ApplicationContext.run(config(properties))

        when:
        context.getBean(PushGateway).push()

        then:
        requests.size() == 1
        requests[0].authorization == 'Bearer secret-token'
        requests[0].contentType.startsWith(contentType)

        cleanup:
        context.close()

        where:
        format            | contentType
        null              | 'application/vnd.google.protobuf'
        'PROMETHEUS_TEXT' | 'text/plain'
        'prometheus-text' | 'text/plain'
    }

    void "the metrics are pushed with the configured scheme"() {
        given:
        ApplicationContext context = ApplicationContext.run(config([
                (PROMETHEUS_PUSHGATEWAY_CONFIG + '.scheme')      : 'https',
                (PROMETHEUS_PUSHGATEWAY_CONFIG + '.read-timeout'): '5s'
        ]))

        when: 'the Pushgateway only accepts HTTP'
        context.getBean(PushGateway).push()

        then:
        thrown(IOException)
        requests.isEmpty()

        cleanup:
        context.close()
    }

    private Map<String, Object> config(Map<String, Object> properties) {
        [
                (PROMETHEUS_PUSHGATEWAY_CONFIG + '.address')      : "127.0.0.1:${pushGateway.address.port}".toString(),
                (PROMETHEUS_PUSHGATEWAY_CONFIG + '.job')          : 'test',
                (PROMETHEUS_PUSHGATEWAY_CONFIG + '.initial-delay'): '1h'
        ] + properties
    }

    private HttpServer startPushGateway() {
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/') { exchange ->
            exchange.requestBody.bytes
            requests << [
                    method       : exchange.requestMethod,
                    path         : exchange.requestURI.path,
                    authorization: exchange.requestHeaders.getFirst('Authorization'),
                    contentType  : exchange.requestHeaders.getFirst('Content-Type')
            ]
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        server
    }
}
