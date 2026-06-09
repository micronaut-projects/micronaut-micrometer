package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.http.annotation.Produces
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

class ObservationHttpHandledExceptionMetricsSpec extends Specification {

    @AutoCleanup
    private ApplicationContext context

    @AutoCleanup
    private EmbeddedServer embeddedServer

    @AutoCleanup
    private HttpClient httpClient

    private MeterRegistry meterRegistry

    void setup() {
        context = ApplicationContext.builder(
            'micronaut.application.name': 'test-app',
            'spec.name': 'ObservationHttpHandledExceptionMetricsSpec'
        ).start()

        embeddedServer = context.getBean(EmbeddedServer).start()
        httpClient = HttpClient.create(embeddedServer.URL)
        meterRegistry = embeddedServer.applicationContext.getBean(MeterRegistry)
    }

    void 'handled exceptions keep the response status in http.server.requests'() {
        when:
        httpClient.toBlocking().exchange(HttpRequest.GET('/metrics/test'))

        then:
        HttpClientResponseException exception = thrown()
        exception.status == HttpStatus.BAD_REQUEST
        meterRegistry.find('http.server.requests')
            .tags('method', 'GET', 'uri', '/metrics/test', 'status', '400')
            .timers()
            .size() == 1
        meterRegistry.find('http.server.requests')
            .tags('method', 'GET', 'uri', '/metrics/test', 'status', '500')
            .timers()
            .isEmpty()
    }

    @Controller('/metrics')
    @Requires(property = 'spec.name', value = 'ObservationHttpHandledExceptionMetricsSpec')
    static class TestController {

        @Get('/test')
        String test() {
            throw new GenericException('BAD REQUEST')
        }
    }

    @Produces
    @Singleton
    @Requires(property = 'spec.name', value = 'ObservationHttpHandledExceptionMetricsSpec')
    static class GenericExceptionHandler implements ExceptionHandler<GenericException, HttpResponse<?>> {

        @Override
        HttpResponse<String> handle(HttpRequest<?> request, GenericException exception) {
            HttpResponse.badRequest('BAD REQUEST FROM HANDLER')
        }
    }

    static class GenericException extends RuntimeException {
        GenericException(String message) {
            super(message)
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'ObservationHttpHandledExceptionMetricsSpec')
    static class TestFactory {

        @Singleton
        @Primary
        MeterRegistry meterRegistry() {
            new SimpleMeterRegistry()
        }
    }
}
