package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * The {@code micrometer.observations.*} properties applied to the HTTP observations.
 */
class ObservationHttpConfigurationSpec extends Specification {

    static final String SPEC_NAME = 'ObservationHttpConfigurationSpec'

    PollingConditions conditions = new PollingConditions(timeout: 5)

    @AutoCleanup
    EmbeddedServer embeddedServer

    @AutoCleanup
    HttpClient client

    MeterRegistry registry

    private void start(Map<String, Object> properties) {
        embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME] + properties)
        registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
    }

    private Set<String> meterNames() {
        registry.meters*.id*.name as Set<String>
    }

    void 'the HTTP observations can be renamed'() {
        given:
        start([
            'micrometer.observations.http.server.requests.name': 'app.http.server',
            'micrometer.observations.http.client.requests.name': 'app.http.client'
        ])

        when:
        client.toBlocking().retrieve('/configuration/ok')

        then:
        conditions.eventually {
            assert registry.find('app.http.server').tag('uri', '/configuration/ok').timer()?.count() == 1
            assert registry.find('app.http.client').timer()?.count() == 1
        }
        registry.find('http.server.requests').timers().isEmpty()
        registry.find('http.client.requests').timers().isEmpty()
    }

    void 'without long task timers an HTTP request records only its timers'() {
        given:
        start(['micrometer.observations.long-task-timer.enabled': false])

        when:
        client.toBlocking().retrieve('/configuration/ok')

        then:
        conditions.eventually {
            assert registry.find('http.server.requests').timer()?.count() == 1
            assert registry.find('http.client.requests').timer()?.count() == 1
        }
        !meterNames().contains('http.server.requests.active')
        !meterNames().contains('http.client.requests.active')
    }

    void 'the long task timers are enabled by default'() {
        given:
        start([:])

        when:
        client.toBlocking().retrieve('/configuration/ok')

        then:
        conditions.eventually {
            assert registry.find('http.server.requests').timer()?.count() == 1
        }
        meterNames().containsAll(['http.server.requests.active', 'http.client.requests.active'])
    }

    void 'the HTTP client observations can be disabled by name prefix'() {
        given:
        start(['micrometer.observations.enable.http.client': false])

        when:
        client.toBlocking().retrieve('/configuration/ok')

        then:
        conditions.eventually {
            assert registry.find('http.server.requests').timer()?.count() == 1
        }
        registry.find('http.client.requests').timers().isEmpty()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/configuration')
    static class ConfigurationController {

        @Get('/ok')
        String ok() { 'ok' }
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class TestFactory {

        @Singleton
        @Primary
        MeterRegistry meterRegistry() {
            new SimpleMeterRegistry()
        }
    }
}
