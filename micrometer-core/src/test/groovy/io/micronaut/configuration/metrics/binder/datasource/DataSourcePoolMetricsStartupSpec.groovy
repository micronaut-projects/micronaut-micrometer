package io.micronaut.configuration.metrics.binder.datasource

import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import org.jspecify.annotations.Nullable
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
        PoolState state = new PoolState(dataSource: Stub(PoolDataSource), skipBetaMetadata: skipBetaMetadata)
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
            properties["micronaut.metrics.binders.${disabledBinder}.enabled".toString()] = false
        }
        ApplicationContext context = ApplicationContext.builder(properties).singletons(state).build()
        SimpleMeterRegistry lateRegistry = new SimpleMeterRegistry()

        when:
        context.start()

        then:
        state.initialized == 2
        MeterRegistry registry = context.getBean(MeterRegistry)
        registry.find('jdbc.connections.active').tag('name', 'alpha').gauge()?.value() == (disabledBinder == 'jdbc' ? null : 1)
        registry.find('jdbc.connections.active').tag('name', 'beta').gauge()?.value() == (disabledBinder == 'jdbc' || skipBetaMetadata ? null : 1)
        state.metadataCreated == (disabledBinder == 'jdbc' ? 0 : skipBetaMetadata ? 1 : 2)
        !httpRequest || registry.get('http.client.requests').timer().count() == 2

        when:
        String postStartupResponse = requestMetadata(context)

        then:
        postStartupResponse == 'ready'
        registry.get('http.client.requests').timer().count() == (httpRequest ? 3 : 1)

        when:
        if (disabledBinder != 'jdbc') {
            context.getBean(MeterBinder, Qualifiers.byName('alpha')).bindTo(lateRegistry)
        }

        then:
        lateRegistry.find('jdbc.connections.active').tag('name', 'alpha').gauge()?.value() == (disabledBinder == 'jdbc' ? null : 1)
        !lateRegistry.find('jdbc.connections.active').tag('name', 'beta').gauge()

        cleanup:
        lateRegistry.close()
        context?.close()
        server?.stop(0)

        where:
        operation                                                   | httpRequest | disabledBinder | skipBetaMetadata
        'resolve the registry'                                      | false       | null           | false
        'make an HTTP request'                                      | true        | null           | false
        'make an HTTP request with JDBC metrics disabled'           | true        | 'jdbc'         | false
        'make an HTTP request without the Netty executor listener'  | true        | 'executor'     | false
        'make an HTTP request with unsupported pool metadata'       | true        | null           | true
    }

    private static String requestMetadata(ApplicationContext context) {
        HttpClient client = context.getBean(HttpClientRegistry).getClient(
                HttpVersionSelection.forClientConfiguration(new DefaultHttpClientConfiguration()),
                context.environment.getProperty('spec.server.url', String).get(), null)
        def request = HttpRequest.GET('/metadata').setAttribute(HttpAttributes.URI_TEMPLATE, '/metadata')
        // Bound the wait so the broken response-filter cycle fails instead of hanging the suite.
        Mono.from(client.retrieve(request)).block(Duration.ofSeconds(5))
    }

    static class PoolState {
        DataSource dataSource
        int initialized
        int metadataCreated
        boolean skipBetaMetadata
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
                assert requestMetadata(context) == 'ready'
            } else {
                context.getBean(MeterRegistry)
            }
            assert state.metadataCreated == 0
            state.initialized++
            state.dataSource
        }

        @Singleton
        @EachBean(DataSource)
        @Nullable
        PoolMetadata metadata(@Parameter String dataSourceName, DataSource dataSource, PoolState state) {
            if (state.skipBetaMetadata && dataSourceName == 'beta') {
                return null
            }
            state.metadataCreated++
            new PoolMetadata(dataSource: dataSource)
        }
    }

    interface PoolDataSource extends DataSource {
    }

    static class PoolMetadata implements DataSourcePoolMetadata<PoolDataSource> {
        PoolDataSource dataSource
        Integer getIdle() { 0 }
        Float getUsage() { 0.5f }
        Integer getActive() { 1 }
        Integer getMax() { 2 }
        Integer getMin() { 0 }
        String getValidationQuery() { null }
        Boolean getDefaultAutoCommit() { null }
    }
}
