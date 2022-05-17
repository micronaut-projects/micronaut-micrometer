package io.micronaut.configuration.metrics.management.endpoint

import io.micrometer.core.annotation.Counted
import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.CompositeMeterRegistryConfigurer
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import javax.validation.constraints.NotBlank

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.core.util.StringUtils.FALSE
import static io.micronaut.http.HttpStatus.OK

@Stepwise
class FilteredMetricsEndpointSpec extends Specification {

    private static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY)         : getClass().simpleName,
            'endpoints.metrics.sensitive': false,
            (MICRONAUT_METRICS_ENABLED)  : true
    ])

    @Shared
    ApplicationContext context = embeddedServer.applicationContext

    void "warm up the server"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.URL)

        expect:
        client.toBlocking().exchange(HttpRequest.GET('/filtered/hello/fred'), String).body() == "Hello Fred"
        client.toBlocking().exchange(HttpRequest.GET('/filtered/rxjava2/fred'), String).body() == "Hello Fred"
    }

    void "test the filter beans are available"() {
        expect:
        context.getBeansOfType(MeterFilter)?.size() == 4
        CompositeMeterRegistryConfigurer configurer = context.getBean(MeterRegistryConfigurer)
        configurer.filters.size() == 4
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)
    }

    @IgnoreIf({ env["CI"] })
    void "test metrics endpoint with filtered metrics"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.URL)

        when:
        ApplicationContext context = embeddedServer.applicationContext

        then:
        context.getBeansOfType(MeterFilter)?.size() == 4
        CompositeMeterRegistryConfigurer configurer = context.getBean(MeterRegistryConfigurer)
        configurer.filters.size() == 4
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)

        when:
        Map result = waitForResponse(client)

        then:
        result.names.size() == 1
        !result.names[0].toString().startsWith("system")

        cleanup:
        client.close()
    }

    private Map waitForResponse(HttpClient client, Integer loopCount = 1) {
        if (loopCount > 5) {
            throw new RuntimeException("Too many attempts to get metrics, failed!")
        }

        def response = client.toBlocking().exchange("/metrics", Map)
        Map result = response?.body()
        if (!(result?.names?.size() > 0) || response?.status() != OK) {
            sleep(500)
            waitForResponse(client, loopCount + 1)
        } else {
            return result
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = FALSE)
    static class HelloController {

        @Get("/filtered/hello/{name}")
        Mono<String> hello(@NotBlank String name) {
            return Mono.just('Hello ' + name.capitalize())
        }

        @Timed
        @Counted
        @Get("/filtered/rxjava2/{name}")
        Single<String> rxjava(@NotBlank String name) {
            return Single.just('Hello ' + name.capitalize())
        }
    }

    @Factory
    static class FilteredMetricsEndpointSpecBeanFactory {

        @Bean
        @Singleton
        @Context
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = FALSE)
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry()
        }

        @Bean
        @Singleton
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = FALSE)
        MeterFilter denyNameStartsWithJvmFilter() {
            return MeterFilter.denyNameStartsWith("system")
        }

        @Bean
        @Singleton
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = FALSE)
        MeterFilter maximumAllowableMetricsFilter() {
            return MeterFilter.maximumAllowableMetrics(1)
        }
    }
}
