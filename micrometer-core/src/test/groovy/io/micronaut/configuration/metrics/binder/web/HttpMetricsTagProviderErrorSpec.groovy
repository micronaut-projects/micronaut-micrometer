package io.micronaut.configuration.metrics.binder.web

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * The failure that the server timer records is the failure passed to the tag providers.
 */
class HttpMetricsTagProviderErrorSpec extends Specification {

    static final String SPEC_NAME = 'HttpMetricsTagProviderErrorSpec'

    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])

    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    MeterRegistry registry = embeddedServer.applicationContext.getBean(MeterRegistry)

    PollingConditions conditions = new PollingConditions(timeout: 5)

    void "an error route that answers successfully passes no failure to the tag providers"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/provider-error/recovered', String)

        then:
        response.body() == 'recovered'
        conditions.eventually {
            def timer = registry.find(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/provider-error/recovered').timer()
            assert timer != null
            assert timer.id.getTag('status') == '200'
            assert timer.id.getTag('exception') == 'none'
            assert timer.id.getTag('provided-failure') == 'none'
        }
    }

    void "a failed request passes its failure to the tag providers"() {
        when:
        client.toBlocking().exchange('/provider-error/failed', String)

        then:
        thrown(HttpClientResponseException)
        conditions.eventually {
            def timer = registry.find(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/provider-error/failed').timer()
            assert timer != null
            assert timer.id.getTag('status') == '500'
            assert timer.id.getTag('exception') == 'UnsupportedOperationException'
            assert timer.id.getTag('provided-failure') == 'UnsupportedOperationException'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/provider-error')
    static class ProviderErrorController {

        @Get('/recovered')
        String recovered() {
            throw new IllegalStateException('recovered by the error route')
        }

        @Get('/failed')
        String failed() {
            throw new UnsupportedOperationException('not recovered')
        }

        @Error(exception = IllegalStateException)
        HttpResponse<String> recover() {
            HttpResponse.ok('recovered')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class FailureTagProvider implements HttpMetricsTagProvider {

        @Override
        Iterable<Tag> getTags(HttpRequest<?> request, HttpResponse<?> response, Throwable throwable) {
            [Tag.of('provided-failure', throwable == null ? 'none' : throwable.class.simpleName)]
        }
    }
}
