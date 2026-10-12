package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationPredicate
import io.micrometer.observation.ObservationRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.micrometer.observation.http.AbstractObservationFilter
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * When the observation registry is a no-op (e.g. no handler) or the HTTP observations are
 * disabled, the HTTP filters do nothing.
 */
class ObservationHttpNoopRegistrySpec extends Specification {

    static final String SPEC_NAME = 'ObservationHttpNoopRegistrySpec'

    @AutoCleanup
    EmbeddedServer embeddedServer

    void 'server and client requests work when the observation registry is a no-op'() {
        given:
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                : SPEC_NAME,
            'micronaut.metrics.enabled': false
        ])
        ApplicationContext context = embeddedServer.applicationContext
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)
        NoopClient declarativeClient = context.getBean(NoopClient)

        expect:
        context.getBean(ObservationRegistry).isNoop()
        context.getBeansOfType(MeterRegistry).isEmpty()

        and: 'the server and the clients answer, and no observation is attached to the requests'
        client.toBlocking().retrieve(HttpRequest.GET('/noop/attribute')) == 'false'
        declarativeClient.attribute() == 'false'

        cleanup:
        client?.close()
    }

    void 'server and client requests work when a predicate disables their observations'() {
        given:
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'        : SPEC_NAME,
            'spec.disable-http': true
        ])
        ApplicationContext context = embeddedServer.applicationContext
        HttpClient client = context.createBean(HttpClient, embeddedServer.URL)
        MeterRegistry meterRegistry = context.getBean(MeterRegistry)

        expect:
        !context.getBean(ObservationRegistry).isNoop()
        client.toBlocking().retrieve(HttpRequest.GET('/noop/attribute')) == 'false'
        context.getBean(NoopClient).attribute() == 'false'
        meterRegistry.find('http.server.requests').timers().isEmpty()
        meterRegistry.find('http.client.requests').timers().isEmpty()
        meterRegistry.find('http.server.requests.active').longTaskTimers().isEmpty()

        cleanup:
        client?.close()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Requires(property = 'spec.disable-http')
    @Singleton
    static class DisableHttpPredicate implements ObservationPredicate {
        @Override
        boolean test(String name, Observation.Context context) {
            !name.startsWith('http.')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Client('/noop')
    static interface NoopClient {
        @Get('/attribute')
        String attribute()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/noop')
    static class NoopController {

        @Get('/attribute')
        String attribute(HttpRequest<?> request) {
            String.valueOf(request.getAttribute(AbstractObservationFilter.MICROMETER_OBSERVATION_ATTRIBUTE_KEY).isPresent())
        }
    }
}
