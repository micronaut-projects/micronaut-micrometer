package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawHttpClientRegistry
import jakarta.inject.Singleton
import spock.lang.Specification

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ELEMENT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.EXECUTION_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GLOBAL
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GROUP
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.PARENT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.QUEUE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WAIT_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WORKER
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot

class NettyQueueMetricsStartupSpec extends Specification {

    private static final String SPEC_NAME = 'NettyQueueMetricsStartupSpec'
    // Only client construction is needed; the test sends no HTTP requests.
    private static final String CLIENT_ID = 'http://localhost:1'

    // With queue metrics enabled, the eager client builds the event loop group factory,
    // which also builds the instrumented task queue factory. If that factory resolves the
    // registry, ClientDependentMeterBinder requests the client registry that is still being
    // created, and startup fails with a circular dependency.
    void "eager client starts with queue metrics when a meter binder needs the same client"() {
        given:
        ApplicationContext context = ApplicationContext.builder([
                'spec.name': SPEC_NAME,
                'micronaut.metrics.enabled': true,
                'micronaut.metrics.binders.netty.queues.enabled': true,
                'micronaut.netty.event-loops.default.num-threads': 1
        ]).build()

        when:
        context.start()

        then:
        context.getBean(StartupHttpClient).client.is(context.getBean(ClientDependentMeterBinder).client)
        MeterRegistry registry = context.getBean(MeterRegistry)
        [PARENT, WORKER].every { String group ->
            registry.find(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME)).tag(GROUP, group).timer() &&
                    registry.find(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME)).tag(GROUP, group).timer() &&
                    registry.find(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT)).tag(GROUP, group).counter()
        }

        cleanup:
        context.close()
    }

    private static RawHttpClient resolveClient(RawHttpClientRegistry registry) {
        registry.getRawClient(
                HttpVersionSelection.forClientConfiguration(new DefaultHttpClientConfiguration()),
                CLIENT_ID,
                null)
    }

    @Context
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class StartupHttpClient {
        final RawHttpClient client

        StartupHttpClient(RawHttpClientRegistry registry) {
            client = resolveClient(registry)
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class ClientDependentMeterBinderFactory {
        @Singleton
        ClientDependentMeterBinder meterBinder(RawHttpClientRegistry registry) {
            new ClientDependentMeterBinder(resolveClient(registry))
        }
    }

    static class ClientDependentMeterBinder implements MeterBinder {
        final RawHttpClient client

        ClientDependentMeterBinder(RawHttpClient client) {
            this.client = client
        }

        @Override
        void bindTo(MeterRegistry registry) {
        }
    }
}
