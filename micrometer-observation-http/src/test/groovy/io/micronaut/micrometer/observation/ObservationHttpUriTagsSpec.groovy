package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ObservationHttpUriTagsSpec extends Specification {

    void "the uri tags guard applies to observation based http meters"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                        : 'ObservationHttpUriTagsSpec',
                'micronaut.metrics.binders.web.server.max-uri-tags': 2,
                'micronaut.metrics.binders.web.client.max-uri-tags': 1
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)
        MeterRegistry registry = server.applicationContext.getBean(MeterRegistry)

        when:
        ['one', 'two', 'three'].each { client.toBlocking().retrieve(HttpRequest.GET("/observation-uri-tags/$it")) }

        then:
        new PollingConditions().eventually {
            assert registry.find('http.server.requests').timers()*.id*.getTag('uri') as Set ==
                    ['/observation-uri-tags/one', '/observation-uri-tags/two'] as Set
            assert registry.find('http.client.requests').timers().size() == 1
        }

        cleanup:
        client.close()
        server.close()
    }

    @Requires(property = 'spec.name', value = 'ObservationHttpUriTagsSpec')
    @Controller('/observation-uri-tags')
    static class UriTagsController {

        @Get('/one')
        String one() { 'one' }

        @Get('/two')
        String two() { 'two' }

        @Get('/three')
        String three() { 'three' }
    }
}
