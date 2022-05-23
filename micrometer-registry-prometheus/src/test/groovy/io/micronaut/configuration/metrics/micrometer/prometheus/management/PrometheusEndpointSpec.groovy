package io.micronaut.configuration.metrics.micrometer.prometheus.management

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class PrometheusEndpointSpec extends Specification {

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

    @Ignore
    void "test prometheus scrape no descriptions"() {
        given:
        def result = client.toBlocking().retrieve('/prometheus')

        expect:
        result.contains('jvm_memory_used')
        !result.contains('# TYPE')
    }
}
