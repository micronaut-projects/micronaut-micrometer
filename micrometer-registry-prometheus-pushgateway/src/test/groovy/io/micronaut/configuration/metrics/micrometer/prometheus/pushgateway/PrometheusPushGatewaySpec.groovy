package io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.configuration.metrics.micrometer.prometheus.controllers.MetricsTestController
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.prometheus.metrics.exporter.pushgateway.Format
import io.prometheus.metrics.exporter.pushgateway.PushGateway
import spock.lang.Specification

import static io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway.PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_ENABLED
import static io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway.PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_CONFIG


class PrometheusPushGatewaySpec extends Specification {

    void "test push metrics"() {

        when:
        EmbeddedServer embeddedTestServer = ApplicationContext.run(EmbeddedServer, [(PROMETHEUS_PUSHGATEWAY_ENABLED): false])

        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                ['micronaut.http.client.read-timeout': '5m',
                 'micronaut.metrics.export.prometheus.pushgateway.initial-delay': '0s',
                 (PROMETHEUS_PUSHGATEWAY_CONFIG + ".address")                 : embeddedTestServer.getURL().toString().replace("http://", ""),
                 (PROMETHEUS_PUSHGATEWAY_CONFIG + ".basic-auth-username")     : "testUsername",
                 (PROMETHEUS_PUSHGATEWAY_CONFIG + ".basic-auth-password")     : "testPassword",
                 (PROMETHEUS_PUSHGATEWAY_CONFIG + ".job")                     : "test",
                 (PROMETHEUS_PUSHGATEWAY_CONFIG + ".format")                  : Format.PROMETHEUS_TEXT])


        then:
        embeddedServer.applicationContext.containsBean(MeterRegistry)
        embeddedServer.applicationContext.containsBean(CompositeMeterRegistry)
        embeddedServer.applicationContext.containsBean(PushGateway)
        embeddedServer.applicationContext.containsBean(MetricsTestController)
        embeddedTestServer.applicationContext.containsBean(MetricsTestController)
        def client = embeddedServer.applicationContext.createBean(HttpClient, embeddedTestServer.getURL()).toBlocking()

        when:
        Thread.sleep(10000)
        String resp = client.retrieve(HttpRequest.GET("/metrics/job/test"), String)

        then:
        resp == "Basic dGVzdFVzZXJuYW1lOnRlc3RQYXNzd29yZA=="

        cleanup:
        embeddedTestServer.stop()
        embeddedServer.stop()
    }
}
