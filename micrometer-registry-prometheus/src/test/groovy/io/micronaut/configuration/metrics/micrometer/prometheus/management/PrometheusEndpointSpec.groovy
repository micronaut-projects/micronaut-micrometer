package io.micronaut.configuration.metrics.micrometer.prometheus.management

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PrometheusEndpointSpec extends Specification {
    @AutoCleanup('shutdownNow')
    ExecutorService scrapeExecutor = Executors.newSingleThreadExecutor()

    def setup() {
        def registry = embeddedServer.applicationContext.getBean(MeterRegistry)

        new JvmMemoryMetrics().bindTo(registry)
        new ProcessorMetrics().bindTo(registry)
    }

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'endpoints.prometheus.sensitive'                  : false,
            'micronaut.metrics.export.prometheus.descriptions': false
    ])

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    void "test prometheus scrape"() {
        expect:
        client.toBlocking().retrieve('/prometheus').contains('jvm_memory_used')
    }

    void "test prometheus scrape streams output"() {
        given:
        def registry = Mock(PrometheusMeterRegistry)
        def endpoint = new PrometheusEndpoint(registry, scrapeExecutor)

        when:
        String result
        endpoint.scrape().withCloseable { inputStream ->
            result = inputStream.getText(StandardCharsets.UTF_8.name())
        }

        then:
        result == 'jvm_memory_used 1.0\n'
        1 * registry.scrape(_ as OutputStream) >> { OutputStream outputStream ->
            outputStream.write('jvm_memory_used 1.0\n'.getBytes(StandardCharsets.UTF_8))
        }
        0 * registry.scrape()
    }

    @PendingFeature
    void "test prometheus scrape no descriptions"() {
        given:
        def result = client.toBlocking().retrieve('/prometheus')

        expect:
        result.contains('jvm_memory_used')
        !result.contains('# TYPE')
    }
}
