package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawHttpClientRegistry
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Unroll

class NettyMetricsLateClientStartupSpec extends Specification {

    private static final String SPEC_NAME = 'NettyMetricsLateClientStartupSpec'
    private static final String CLIENT_ID = 'http://localhost:1'

    // No eager client is registered, so the pending queue is empty at StartupEvent.
    // The registry must still be initialized there: otherwise the first later client
    // lookup initializes it inside the client cache and the meter binder re-enters
    // that same cache entry. Only client construction is needed; no requests are sent.
    void "client lookup after startup is safe when a meter binder needs the same client"() {
        given:
        ApplicationContext context = ApplicationContext.builder([
                'spec.name': SPEC_NAME,
                'micronaut.metrics.enabled': true,
                'micronaut.netty.event-loops.default.num-threads': 1
        ]).build()

        when:
        context.start()

        then:
        !context.getActiveBeanRegistrations(MeterRegistry).empty

        when:
        RawHttpClient client = resolveClient(context.getBean(RawHttpClientRegistry))

        then:
        client
        LateClientMeterBinder binder = context.getBean(LateClientMeterBinder)
        binder.client.is(client)
        binder.bound
        MeterRegistry registry = context.getBean(MeterRegistry)
        registry.find('executor.queued').tag('name', 'default').gauge()
        registry.find('executor.pool.size').tag('name', 'default').gauge()

        cleanup:
        context.close()
    }

    // A StartupEvent listener ordered after the contributor must find the registry already
    // initialized, so its first client lookup does not initialize it inside the client cache.
    void "client lookup from a later startup listener is safe when a meter binder needs the same client"() {
        given:
        ApplicationContext context = ApplicationContext.builder([
                'spec.name': SPEC_NAME,
                'spec.startup-listener': true,
                'micronaut.metrics.enabled': true,
                'micronaut.netty.event-loops.default.num-threads': 1
        ]).build()

        when:
        context.start()

        then:
        LateStartupListener listener = context.getBean(LateStartupListener)
        listener.registryInitialized
        listener.client
        context.getBean(LateClientMeterBinder).client.is(listener.client)
        context.getBean(MeterRegistry).find('executor.pool.size').tag('name', 'default').gauge()

        cleanup:
        context.close()
    }

    @Unroll
    void "disabling #property keeps the contributor and registry uninitialized"() {
        given:
        Map<String, Object> properties = [
                'spec.name': SPEC_NAME,
                'micronaut.metrics.enabled': true
        ]
        properties[property] = false
        ApplicationContext context = ApplicationContext.builder(properties).build()

        when:
        context.start()

        then:
        !context.containsBean(NettyEventLoopGroupMetricsContributor)
        context.getActiveBeanRegistrations(MeterRegistry).empty

        cleanup:
        context.close()

        where:
        property << ['micronaut.metrics.enabled', 'micronaut.metrics.binders.executor.enabled']
    }

    private static RawHttpClient resolveClient(RawHttpClientRegistry registry) {
        registry.getRawClient(
                HttpVersionSelection.forClientConfiguration(new DefaultHttpClientConfiguration()),
                CLIENT_ID,
                null)
    }

    @Singleton
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Requires(property = 'spec.startup-listener', value = 'true')
    static class LateStartupListener implements ApplicationEventListener<StartupEvent> {
        private final BeanContext beanContext
        RawHttpClient client
        boolean registryInitialized

        LateStartupListener(BeanContext beanContext) {
            this.beanContext = beanContext
        }

        @Override
        void onApplicationEvent(StartupEvent event) {
            registryInitialized = !beanContext.getActiveBeanRegistrations(MeterRegistry).empty
            client = resolveClient(beanContext.getBean(RawHttpClientRegistry))
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class LateClientMeterBinderFactory {
        @Singleton
        LateClientMeterBinder meterBinder(RawHttpClientRegistry registry) {
            new LateClientMeterBinder(resolveClient(registry))
        }
    }

    static class LateClientMeterBinder implements MeterBinder {
        final RawHttpClient client
        boolean bound

        LateClientMeterBinder(RawHttpClient client) {
            this.client = client
        }

        @Override
        void bindTo(MeterRegistry registry) {
            bound = true
        }
    }
}
