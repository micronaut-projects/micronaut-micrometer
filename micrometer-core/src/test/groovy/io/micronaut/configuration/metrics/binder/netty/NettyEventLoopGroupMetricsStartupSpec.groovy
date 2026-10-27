package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawHttpClientRegistry
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoopGroup
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class NettyEventLoopGroupMetricsStartupSpec extends Specification {

    private static final String SPEC_NAME = 'NettyEventLoopGroupMetricsStartupSpec'
    // Only client construction is needed; the test sends no HTTP requests.
    private static final String CLIENT_ID = 'http://localhost:1'

    // Eager HTTP client creation also creates its Netty event loop. If binding its metrics
    // resolves the registry here, ClientDependentMeterBinder requests the same client
    // while its cache entry is still being computed, causing "Recursive update".
    // Deferring Netty metrics until StartupEvent lets client construction finish first.
    void "meter binder can resolve the HTTP client whose event loop is being created"() {
        given:
        ApplicationContext context = ApplicationContext.builder([
                'spec.name': SPEC_NAME,
                'micronaut.metrics.enabled': true,
                'micronaut.netty.event-loops.default.num-threads': 1
        ]).build()

        when:
        context.start()

        then:
        StartupHttpClient startupClient = context.getBean(StartupHttpClient)
        ClientDependentMeterBinder binder = context.getBean(ClientDependentMeterBinder)
        binder.client.is(startupClient.client)
        binder.bound
        MeterRegistry registry = context.getBean(MeterRegistry)
        registry.find('executor.queued').tag('name', 'default').gauge()
        registry.find('executor.pool.size').tag('name', 'default').gauge()

        cleanup:
        context.close()
    }

    void "event loop metrics resolve the registry only after startup"() {
        given:
        NettyEventLoopGroupMetricsContributor contributor = new NettyEventLoopGroupMetricsContributor()
        BeanProvider<MeterRegistry> provider = Mock()
        MeterRegistry registry = new SimpleMeterRegistry()
        EventLoopGroup group = new DefaultEventLoop()

        when:
        def result = contributor.bindTo(provider, group, 'early', [])

        then:
        result.is(group)
        !registry.find('executor.pool.size').tag('name', 'early').gauge()
        0 * provider.get()

        when:
        contributor.onApplicationEvent(new StartupEvent(Stub(BeanContext)))

        then:
        1 * provider.get() >> registry
        registry.find('executor.queued').tag('name', 'early').gauge()
        registry.find('executor.pool.size').tag('name', 'early').gauge()

        when:
        contributor.onApplicationEvent(new StartupEvent(Stub(BeanContext)))

        then:
        0 * provider.get()

        cleanup:
        shutdown(group)
        registry.close()
    }

    void "event loop groups created after startup are bound immediately"() {
        given:
        NettyEventLoopGroupMetricsContributor contributor = new NettyEventLoopGroupMetricsContributor()
        contributor.onApplicationEvent(new StartupEvent(Stub(BeanContext)))
        BeanProvider<MeterRegistry> provider = Mock()
        MeterRegistry registry = new SimpleMeterRegistry()
        EventLoopGroup group = new DefaultEventLoop()

        when:
        def result = contributor.bindTo(provider, group, 'late', [])

        then:
        1 * provider.get() >> registry
        result.is(group)
        registry.find('executor.queued').tag('name', 'late').gauge()
        registry.find('executor.pool.size').tag('name', 'late').gauge()

        cleanup:
        shutdown(group)
        registry.close()
    }

    void "closed event loop groups are not bound at startup"() {
        given:
        NettyEventLoopGroupMetricsContributor contributor = new NettyEventLoopGroupMetricsContributor()
        BeanProvider<MeterRegistry> provider = Mock()
        EventLoopGroup group = new DefaultEventLoop()
        contributor.bindTo(provider, group, 'closed', [])
        shutdown(group)

        when:
        contributor.onApplicationEvent(new StartupEvent(Stub(BeanContext)))

        then:
        0 * provider.get()

        cleanup:
        shutdown(group)
    }

    void "groups created while resolving the registry are also bound"() {
        given:
        NettyEventLoopGroupMetricsContributor contributor = new NettyEventLoopGroupMetricsContributor()
        MeterRegistry registry = new SimpleMeterRegistry()
        EventLoopGroup first = new DefaultEventLoop()
        EventLoopGroup second = new DefaultEventLoop()
        int lookups = 0
        BeanProvider<MeterRegistry> provider
        provider = Stub(BeanProvider) {
            get() >> {
                // Registry initialization can create another group while pending bindings are drained.
                if (lookups++ == 0) {
                    contributor.bindTo(provider, second, 'during-binding', [])
                }
                registry
            }
        }
        contributor.bindTo(provider, first, 'before-binding', [])

        when:
        contributor.onApplicationEvent(new StartupEvent(Stub(BeanContext)))

        then:
        lookups == 2
        registry.find('executor.pool.size').tag('name', 'before-binding').gauge()
        registry.find('executor.pool.size').tag('name', 'during-binding').gauge()

        cleanup:
        shutdown(first)
        shutdown(second)
        registry.close()
    }

    private static RawHttpClient resolveClient(RawHttpClientRegistry registry) {
        registry.getRawClient(
                HttpVersionSelection.forClientConfiguration(new DefaultHttpClientConfiguration()),
                CLIENT_ID,
                null)
    }

    private static void shutdown(EventLoopGroup group) {
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly()
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
        boolean bound

        ClientDependentMeterBinder(RawHttpClient client) {
            this.client = client
        }

        @Override
        void bindTo(MeterRegistry registry) {
            bound = true
        }
    }
}
