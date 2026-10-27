package io.micronaut.micrometer.test

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.AbstractPropertySourceLoader
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class MicrometerTestSupportSpec extends Specification {

    void "exporters are disabled in the test environment"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder().environments(Environment.TEST).start()

        expect:
        !ctx.containsBean(PrometheusMeterRegistry)
        ctx.getBean(CompositeMeterRegistry).registries*.class == [SimpleMeterRegistry]

        cleanup:
        ctx.close()
    }

    void "exporters are not disabled outside of the test environment"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments('dev')
                .start()

        expect:
        !ctx.environment.activeNames.contains(Environment.TEST)
        ctx.containsBean(PrometheusMeterRegistry)

        cleanup:
        ctx.close()
    }

    void "configuration files override the test default: #config"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .environments(Environment.TEST)
                .propertySources(PropertySource.of('application-test', config, AbstractPropertySourceLoader.DEFAULT_POSITION))
                .start()

        expect:
        ctx.containsBean(PrometheusMeterRegistry)

        cleanup:
        ctx.close()

        where:
        config << [
                ['micronaut.metrics.export.defaults.enabled': true],
                ['micronaut.metrics.export.prometheus.enabled': true]
        ]
    }

    void "the observation registry is a test observation registry that still records meters"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder().environments(Environment.TEST).start()
        ObservationRegistry observationRegistry = ctx.getBean(ObservationRegistry)
        MeterRegistry meterRegistry = ctx.getBean(MeterRegistry)

        when:
        Observation.createNotStarted('test.observation', observationRegistry)
                .lowCardinalityKeyValue('key', 'value')
                .observe {}

        then:
        observationRegistry instanceof TestObservationRegistry
        ctx.getBean(TestObservationRegistry).is(observationRegistry)
        TestObservationRegistryAssert.assertThat((TestObservationRegistry) observationRegistry)
                .hasObservationWithNameEqualTo('test.observation')
                .that()
                .hasLowCardinalityKeyValue('key', 'value')
        MeterRegistryAssert.assertThat(meterRegistry)
                .hasTimerWithNameAndTags('test.observation', Tags.of('key', 'value'), 1)

        cleanup:
        ctx.close()
    }

    void "the test observation registry applies the micrometer.observations configuration"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .environments(Environment.TEST)
                .properties([
                        'micrometer.observations.common-key-value.region': 'eu',
                        'micrometer.observations.enable.disabled'        : false,
                        'micrometer.observations.long-task-timer.enabled': false
                ])
                .start()
        TestObservationRegistry observationRegistry = ctx.getBean(TestObservationRegistry)
        MeterRegistry meterRegistry = ctx.getBean(MeterRegistry)

        when:
        Observation.createNotStarted('test.configured', observationRegistry).observe {}
        Observation disabled = Observation.createNotStarted('disabled.observation', observationRegistry)
        disabled.observe {}

        then: 'the common key values are added'
        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo('test.configured')
                .that()
                .hasLowCardinalityKeyValue('region', 'eu')
        MeterRegistryAssert.assertThat(meterRegistry)
                .hasTimerWithNameAndTags('test.configured', Tags.of('region', 'eu'), 1)

        and: 'the disabled observation is a no-op'
        disabled.isNoop()
        meterRegistry.find('disabled.observation').timers().isEmpty()

        and: 'no long task timer is started'
        meterRegistry.find('test.configured.active').longTaskTimers().isEmpty()

        cleanup:
        ctx.close()
    }

    void "the test observation registry can be disabled"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .environments(Environment.TEST)
                .properties(['micrometer.observations.test-registry.enabled': false])
                .start()

        expect:
        !(ctx.getBean(ObservationRegistry) instanceof TestObservationRegistry)

        cleanup:
        ctx.close()
    }

    void "meter registry assertions"() {
        given:
        MeterRegistry registry = new SimpleMeterRegistry()
        registry.counter('my.counter', 'a', 'b').increment(2)
        registry.timer('my.timer', 'c', 'd').record { }

        expect:
        MeterRegistryAssert.assertThat(registry)
                .hasMeterWithName('my.counter')
                .doesNotHaveMeterWithName('other')
                .hasMeterWithNameAndTags('my.counter', Tags.of('a', 'b'))
                .doesNotHaveMeterWithNameAndTags('my.counter', Tags.of('a', 'x'))
                .hasCounterWithNameAndTags('my.counter', Tags.of('a', 'b'), 2)
                .hasTimerWithNameAndTags('my.timer', Tags.of('c', 'd'), 1)

        when:
        MeterRegistryAssert.assertThat(registry).hasMeterWithName('other')

        then:
        AssertionError e = thrown()
        e.message.contains('Expected a meter with name <other>')
        e.message.contains('my.counter')

        when:
        MeterRegistryAssert.assertThat(registry).hasCounterWithNameAndTags('my.counter', Tags.of('a', 'b'), 3)

        then:
        e = thrown()
        e.message.contains('to have count <3.0> but it has <2.0>')

        when:
        MeterRegistryAssert.assertThat(registry).hasTimerWithNameAndTags('my.counter', Tags.empty(), 1)

        then:
        e = thrown()
        e.message.contains('is not a timer')
    }
}
