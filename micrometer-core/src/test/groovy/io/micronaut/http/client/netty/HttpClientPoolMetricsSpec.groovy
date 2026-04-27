package io.micronaut.http.client.netty

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
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

    void "test http client pool metrics meters are present"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                (MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled"): true,
                "spec.name": getClass().getSimpleName()
        ])
        MeterRegistry registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        DummyClient client = embeddedServer.applicationContext.getBean(DummyClient)
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
    }

    @Requires(property = "spec.name", value = "HttpClientPoolMetricsSpec")
    @Client("/pool-metrics")
    private static interface DummyClient {
        @Get
        String root()
    }

    @Requires(property = "spec.name", value = "HttpClientPoolMetricsSpec")
    @Controller("/pool-metrics")
    private static class DummyController {
        @Get
        String root() {
            "root"
        }
    }
}
