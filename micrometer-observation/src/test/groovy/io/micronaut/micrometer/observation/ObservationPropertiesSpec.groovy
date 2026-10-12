package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import io.micrometer.observation.ObservationPredicate
import io.micrometer.observation.ObservationRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification

class ObservationPropertiesSpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    private ObservationRegistry start(Map<String, Object> properties) {
        context = ApplicationContext.run(properties)
        context.registerSingleton(MeterRegistry, new SimpleMeterRegistry())
        context.getBean(ObservationRegistry)
    }

    private boolean observed(ObservationRegistry registry, String name) {
        Observation observation = Observation.start(name, registry)
        observation.stop()
        !observation.isNoop()
    }

    void 'nothing is filtered and no filter or predicate is registered by default'() {
        when:
        ObservationRegistry registry = start([:])

        then:
        context.getBeansOfType(ObservationPredicate).isEmpty()
        context.getBeansOfType(ObservationFilter).isEmpty()
        observed(registry, 'http.server.requests')
        observed(registry, 'anything')
    }

    void 'observations are enabled and disabled by name prefix: #description'() {
        when:
        ObservationRegistry registry = start(properties)

        then:
        context.getBeansOfType(ObservationPredicate).size() == 1
        expected.every { name, enabled -> observed(registry, name) == enabled }

        where:
        description                     | properties                                                                                                   | expected
        'a disabled prefix'             | ['micrometer.observations.enable.http.server': false]                                                        | ['http.server.requests': false, 'http.server': false, 'http.client.requests': true, 'http.serverless': true, 'other': true]
        'the longest prefix wins'       | ['micrometer.observations.enable.http': false, 'micrometer.observations.enable.http.client': true]          | ['http.server.requests': false, 'http.client.requests': true, 'http': false]
        'all is the root'               | ['micrometer.observations.enable.all': false, 'micrometer.observations.enable.http.client.requests': true] | ['http.server.requests': false, 'http.client.requests': true, 'other': false]
        'a prefix with a hyphen'        | ['micrometer.observations.enable.my-app': false]                                                             | ['my-app.work': false, 'myapp.work': true]
    }

    void 'the predicate caches its decision by name'() {
        given:
        def predicate = new PropertiesObservationPredicate(['jdbc': false, 'all': true])
        def context = new Observation.Context()

        expect:
        !predicate.test('jdbc.query', context)
        !predicate.test('jdbc.query', context)
        predicate.test('http.server.requests', context)
        predicate.test(null, context)
    }

    void 'the common key values filter is registered when configured'() {
        when:
        ObservationRegistry registry = start(['micrometer.observations.common-key-value.region': 'eu'])
        Observation observation = Observation.start('test.common', registry)
        observation.stop()

        then:
        context.getBeansOfType(ObservationFilter).size() == 1
        observation.context.getLowCardinalityKeyValue('region').value == 'eu'
    }

    void 'the observations start a long task timer unless it is disabled: #enabled'() {
        given:
        Map<String, Object> properties = enabled == null ? [:] : ['micrometer.observations.long-task-timer.enabled': enabled]
        ObservationRegistry registry = start(properties)
        MeterRegistry meterRegistry = context.getBean(MeterRegistry)

        when:
        Observation observation = Observation.start('test.ltt', registry)
        boolean activeWhileRunning = meterRegistry.find('test.ltt.active').longTaskTimer()?.activeTasks() == 1
        observation.stop()

        then:
        meterRegistry.find('test.ltt').timer().count() == 1
        (meterRegistry.find('test.ltt.active').longTaskTimer() != null) == expected
        activeWhileRunning == expected

        where:
        enabled | expected
        null    | true
        true    | true
        false   | false
    }
}
