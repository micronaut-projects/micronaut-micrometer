package io.micronaut.configuration.metrics.serialization

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class MetricsMicronautSerialization extends Specification {

    void "test metrics endpoint with micronaut-serialization module"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder()
                .overrideConfigLocations().run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

        when:
        def response = client.toBlocking().exchange("/metrics/", Map)
        Map result = response.body() as Map

        then:
        result.names
        List names = result.names as List
        names
        names.containsAll("executor.completed", "executor.queued")

        cleanup:
        client.close()
        embeddedServer.close()
    }
}
