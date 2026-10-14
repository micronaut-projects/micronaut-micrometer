package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

import static io.micronaut.core.util.StringUtils.FALSE

class CompositeMeterRegistryConfigurationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'spec.name'                 : 'CompositeMeterRegistryConfigurationSpec',
            'micronaut.metrics.tags.app': 'test-app'
    ])

    void "the composite registry is configured once when there are several registries"() {
        given:
        CompositeMeterRegistry composite = context.getBean(CompositeMeterRegistry)
        EventBinder binder = context.getBean(EventBinder)

        expect:
        composite.registries.size() == 2
        binder.bindCount.get() == 1
    }

    void "meter filters are applied once when there are several registries"() {
        given:
        CompositeMeterRegistry composite = context.getBean(CompositeMeterRegistry)

        expect:
        composite.find('prefix.test.events').counter()
        !composite.find('prefix.prefix.test.events').counter()
        composite.registries.every { MeterRegistry registry ->
            registry.find('prefix.test.events').tag('app', 'test-app').counter() &&
                    !registry.find('prefix.prefix.test.events').counter()
        }
    }

    void "a listener based binder records each event once when there are several registries"() {
        given:
        CompositeMeterRegistry composite = context.getBean(CompositeMeterRegistry)
        EventBinder binder = context.getBean(EventBinder)

        when:
        3.times { binder.fire() }

        then: 'each event is counted once across all the meters the binder registered'
        eventCount(composite) == 3
        composite.registries.every { MeterRegistry registry -> eventCount(registry) == 3 }
    }

    private static double eventCount(MeterRegistry registry) {
        registry.meters
                .findAll { it.id.name.endsWith('test.events') }
                .sum(0d) { ((Counter) it).count() } as double
    }

    /**
     * A binder that, like JvmGcMetrics, registers a listener on each call to bindTo.
     */
    static class EventBinder implements MeterBinder {
        final AtomicInteger bindCount = new AtomicInteger()
        final List<Runnable> listeners = []

        @Override
        void bindTo(MeterRegistry registry) {
            bindCount.incrementAndGet()
            Counter counter = Counter.builder('test.events').register(registry)
            listeners << ({ counter.increment() } as Runnable)
        }

        void fire() {
            listeners*.run()
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'CompositeMeterRegistryConfigurationSpec', defaultValue = FALSE)
    static class CompositeMeterRegistryConfigurationSpecFactory {

        @Singleton
        @Named('first')
        SimpleMeterRegistry firstRegistry() {
            new SimpleMeterRegistry()
        }

        @Singleton
        @Named('second')
        SimpleMeterRegistry secondRegistry() {
            new SimpleMeterRegistry()
        }

        @Singleton
        EventBinder eventBinder() {
            new EventBinder()
        }

        @Singleton
        MeterFilter prefixFilter() {
            new MeterFilter() {
                @Override
                Meter.Id map(Meter.Id id) {
                    id.withName('prefix.' + id.name)
                }
            }
        }
    }
}
