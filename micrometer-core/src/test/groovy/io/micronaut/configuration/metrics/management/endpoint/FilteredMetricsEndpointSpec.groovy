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
package io.micronaut.configuration.metrics.management.endpoint

import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.configuration.metrics.aggregator.CompositeMeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import javax.validation.constraints.NotBlank

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

@Slf4j
@Stepwise
class FilteredMetricsEndpointSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    (SPEC_NAME_PROPERTY)         : getClass().simpleName,
                    'endpoints.metrics.sensitive': false,
                    (MICRONAUT_METRICS_ENABLED)  : true
            ]
    )

    @Shared
    ApplicationContext context = embeddedServer.applicationContext

    void "warm up the server"() {
        given:
        RxHttpClient rxClient = RxHttpClient.create(embeddedServer.getURL())

        expect:
        rxClient.exchange(HttpRequest.GET('/filtered/hello/fred'), String).blockingFirst().body() == "Hello Fred"
    }

    void "test the filter beans are available"() {
        expect:
        context.getBeansOfType(MeterFilter.class)?.size() == 4
        CompositeMeterRegistryConfigurer configurer = context.getBean(MeterRegistryConfigurer)
        configurer.filters.size() == 4
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)
    }

    @IgnoreIf({env["CI"]})
    void "test metrics endpoint with filtered metrics"() {
        given:
        RxHttpClient rxClient = RxHttpClient.create(embeddedServer.getURL())

        when:
        ApplicationContext context = embeddedServer.getApplicationContext()

        then:
        context.getBeansOfType(MeterFilter.class)?.size() == 4
        CompositeMeterRegistryConfigurer configurer = context.getBean(MeterRegistryConfigurer)
        configurer.filters.size() == 4
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)

        when:

        def result = waitForResponse(rxClient)

        then:
        result.names.size() == 1
        !result.names[0].toString().startsWith("system")

        cleanup:
        rxClient.close()
    }

    Map waitForResponse(RxHttpClient rxClient, Integer loopCount = 1) {
        if (loopCount > 5) {
            throw new RuntimeException("Too many attempts to get metrics, failed!")
        }

        def response = rxClient.exchange("/metrics", Map).blockingFirst()
        Map result = response?.body()
        log.info("/metrics returned status=${response?.status()} data=${result}")
        if (!(result?.names?.size() > 0) || response?.status() != HttpStatus.OK) {
            Thread.sleep(500)
            log.info("Could not get metrics, retrying attempt $loopCount of 5")
            waitForResponse(rxClient, loopCount + 1)
        } else {
            return result
        }
    }

    @Controller("/")
    @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
    static class HelloController {

        @Get("/filtered/hello/{name}")
        Mono<String> hello(@NotBlank String name) {
            return Mono.just("Hello ${name.capitalize()}".toString())
        }
    }

    @Factory
    static class FilteredMetricsEndpointSpecBeanFactory {

        @Bean
        @Singleton
        @Context
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry()
        }

        @Bean
        @Singleton
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
        MeterFilter denyNameStartsWithJvmFilter() {
            return MeterFilter.denyNameStartsWith("system")
        }

        @Bean
        @Singleton
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
        MeterFilter maximumAllowableMetricsFilter() {
            return MeterFilter.maximumAllowableMetrics(1)
        }
    }
}