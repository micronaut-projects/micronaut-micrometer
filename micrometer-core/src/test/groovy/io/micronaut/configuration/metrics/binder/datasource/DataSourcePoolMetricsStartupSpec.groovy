package io.micronaut.configuration.metrics.binder.datasource

import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import javax.sql.DataSource
import java.time.Duration

class DataSourcePoolMetricsStartupSpec extends Specification {

    private static final String SPEC_NAME = 'DataSourcePoolMetricsStartupSpec'

    @Unroll
    void "datasource initialization can #operation before pool metrics are bound"() {
        given:
        HttpServer server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
        server.createContext('/metadata') { exchange ->
            byte[] response = 'ready'.bytes
            exchange.sendResponseHeaders(200, response.length)
            exchange.responseBody.withCloseable { it.write(response) }
        }
        server.start()
        PoolState state = new PoolState(dataSource: Stub(DataSource))
        Map<String, Object> properties = [
                'spec.name': SPEC_NAME,
                'spec.http-request': httpRequest,
                'spec.pools.alpha.enabled': true,
                'spec.pools.beta.enabled': true,
                'micronaut.metrics.enabled': true,
                'micronaut.netty.event-loops.default.num-threads': 1,
                'spec.server.url': "http://localhost:${server.address.port}"
        ]
        if (disabledBinder) {
            properties["micronaut.metrics.binders.${disabledBinder}.enabled"] = false
        }
        ApplicationContext context = ApplicationContext.builder(properties).singletons(state).build()

        when:
        context.start()

        then:
        state.initialized == 2
        MeterRegistry registry = context.getBean(MeterRegistry)
        registry.find('jdbc.connections.active').tag('name', 'alpha').gauge()?.value() == (disabledBinder == 'jdbc' ? null : 1)
        registry.find('jdbc.connections.active').tag('name', 'beta').gauge()?.value() == (disabledBinder == 'jdbc' ? null : 1)
        state.metadataCreated == (disabledBinder == 'jdbc' ? 0 : 2)
        !httpRequest || registry.get('http.client.requests').timer().count() == 2

        cleanup:
        context?.close()
        server?.stop(0)

        where:
        operation                                                   | httpRequest | disabledBinder
        'resolve the registry'                                      | false       | null
        'make an HTTP request'                                      | true        | null
        'make an HTTP request with JDBC metrics disabled'           | true        | 'jdbc'
        'make an HTTP request without the Netty executor listener'  | true        | 'executor'
    }

    static class PoolState {
        DataSource dataSource
        int initialized
        int metadataCreated
    }

    @EachProperty('spec.pools')
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class PoolConfiguration {
        boolean enabled
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class PoolFactory {

        @Context
        @EachBean(PoolConfiguration)
        DataSource dataSource(PoolConfiguration configuration, ApplicationContext context, PoolState state) {
            if (context.environment.getProperty('spec.http-request', Boolean).orElse(false)) {
                HttpClient client = context.getBean(HttpClientRegistry).getClient(
                        HttpVersionSelection.forClientConfiguration(new DefaultHttpClientConfiguration()),
                        context.environment.getProperty('spec.server.url', String).get(), null)
                // Bound the wait so the broken response-filter cycle fails instead of hanging the suite.
                def request = HttpRequest.GET('/metadata').setAttribute(HttpAttributes.URI_TEMPLATE, '/metadata')
                assert Mono.from(client.retrieve(request)).block(Duration.ofSeconds(5)) == 'ready'
            } else {
                context.getBean(MeterRegistry)
            }
            assert state.metadataCreated == 0
            state.initialized++
            state.dataSource
        }

        @Singleton
        @EachBean(DataSource)
        DataSourcePoolMetadata metadata(DataSource dataSource, PoolState state) {
            state.metadataCreated++
            new PoolMetadata(dataSource: dataSource)
        }
    }

    static class PoolMetadata implements DataSourcePoolMetadata {
        DataSource dataSource
        Integer getIdle() { 0 }
        Float getUsage() { 0.5f }
        Integer getActive() { 1 }
        Integer getMax() { 2 }
        Integer getMin() { 0 }
        String getValidationQuery() { null }
        Boolean getDefaultAutoCommit() { null }
    }
}
