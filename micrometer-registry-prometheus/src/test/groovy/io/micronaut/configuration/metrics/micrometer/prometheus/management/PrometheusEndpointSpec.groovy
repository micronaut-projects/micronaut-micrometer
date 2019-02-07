package io.micronaut.configuration.metrics.micrometer.prometheus.management

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PrometheusEndpointSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'endpoints.prometheus.sensitive':false
    ])
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext
                                                    .createBean(RxHttpClient, embeddedServer.getURL())


    void "test prometheus scrape"() {
        expect:
        client.retrieve('/prometheus').blockingFirst().contains('jvm_memory_used')
    }

}
