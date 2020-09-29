/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.binder.web

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class HttpMetricsSpec extends Specification {

    void "test client / server metrics"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        def context = embeddedServer.getApplicationContext()
        TestClient client = context.getBean(TestClient)

        then:
        client.index() == 'ok'

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)

        Timer serverTimer = registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags('uri','/test-http-metrics').timer()
        Timer clientTimer = registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags('uri','/test-http-metrics').timer()


        then:
        serverTimer != null
        serverTimer.count() == 1
        clientTimer.count() == 1

        when:"A request is sent to the root route"

        then:
        client.root() == 'root'
        registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags('uri','root').timer()
        registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags('uri','root').timer()

        when:"A request is sent with a uri template"
        def result = client.template("foo")

        then:
        result == 'ok foo'
        registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags('uri','/test-http-metrics/{id}').timer()
        registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags('uri','/test-http-metrics/{id}').timer()
		registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags('host','localhost').timer()

        when:
        registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags('uri','/test-http-metrics/foo').timer()

        then:
        thrown(MeterNotFoundException)

        when:"A request is made that returns an error response"
        client.error()

        then:
        thrown(HttpClientResponseException)

        when:
        registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags("status", "409").timer()
        registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags("status", "409").timer()

        then:
        noExceptionThrown()

        when:"A request is made that throws an exception"
        client.throwable()

        then:
        thrown(HttpClientResponseException)

        when:
        registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags("status", "500").timer()
        registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags("status", "500").timer()

        then:
        noExceptionThrown()

		cleanup:
        embeddedServer.close()

	}

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(ClientRequestMetricRegistryFilter).isPresent() == setting
        context.findBean(ServerRequestMeterRegistryFilter).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                           | setting
        MICRONAUT_METRICS_ENABLED     | true
        MICRONAUT_METRICS_ENABLED     | false
        (WebMetricsPublisher.ENABLED) | true
        (WebMetricsPublisher.ENABLED) | false
    }



    @Client('/')
    static interface TestClient {
        @Get
        String root()

        @Get('/test-http-metrics')
        String index()

        @Get("/test-http-metrics/{id}")
        String template(String id)

        @Get("/test-http-metrics/error")
        HttpResponse error()

        @Get("/test-http-metrics/throwable")
        HttpResponse throwable()
    }

    @Controller('/')
    static class TestController {
        @Get
        String root() {
            return "root"
        }

        @Get('/test-http-metrics')
        String index() {
            return "ok"
        }

        @Get("/test-http-metrics/{id}")
        String template(String id) {
            return "ok " + id
        }

        @Get("/test-http-metrics/error")
        HttpResponse error() {
            HttpResponse.status(HttpStatus.CONFLICT)
        }

        @Get("/test-http-metrics/throwable")
        HttpResponse throwable() {
            throw new RuntimeException("error")
        }
    }
}
