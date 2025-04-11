package io.micronaut.configuration.metrics.binder.web


import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class HttpContextMetricsSpec extends Specification {

    void "test disabling metrics"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.metrics.binders.web.enabled' : false, 'spec.name': getClass().getSimpleName()])
        def context = embeddedServer.applicationContext
        TestClient client = context.getBean(TestClient)

        then:
        client.index() == 'ok'

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)

        registry.get(HttpServerMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics').timer()
        registry.get(HttpClientMeterConfig.REQUESTS_METRIC).tags('uri', '/test-http-metrics').timer()

        then:
        thrown(MeterNotFoundException)

        cleanup:
        embeddedServer.close()

    }

    @Requires(property = "spec.name", value = "HttpContextMetricsSpec")
    @Context
    @Client('/')
    static interface TestClient {

        @Get('/test-http-metrics')
        String index()

    }

    @Requires(property = "spec.name", value = "HttpContextMetricsSpec")
    @Context
    @Controller('/')
    static class TestController {

        @Get('/test-http-metrics')
        String index() { "ok" }

    }
}
