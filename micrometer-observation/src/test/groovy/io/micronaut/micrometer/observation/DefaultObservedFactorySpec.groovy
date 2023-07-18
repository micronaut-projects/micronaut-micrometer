package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationFilter
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.handler.DefaultTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler
import io.micrometer.tracing.propagation.Propagator
import spock.lang.Specification

class DefaultObservedFactorySpec extends Specification {

    public MeterRegistry meterRegistryMocked = Mock(MeterRegistry)
    public Tracer tracerMocked = Mock(Tracer)
    public Propagator propagator = Mock(Propagator)


    void 'test no metrics and no trace'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
        ).start()

        then:
        context.getBeansOfType(MeterRegistry).size() == 0
        context.getBeansOfType(Tracer).size() == 0
        context.getBeansOfType(Propagator).size() == 0
        context.getBeansOfType(ObservationHandler).size() == 0
        context.getBeansOfType(ObservationFilter).size() == 1
        context.getBeansOfType(ObservationHandlerGroupingClass).size() == 2
        context.getBeansOfType(ObservationHandlerGrouping).size() == 1
        context.getBeansOfType(ObservationRegistry).size() == 1
    }

    void 'test metrics and no trace'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
        ).start()
        context.registerSingleton(meterRegistryMocked)

        then:
        context.getBeansOfType(MeterRegistry).size() == 1
        context.getBeansOfType(Tracer).size() == 0
        context.getBeansOfType(Propagator).size() == 0
        context.getBeansOfType(ObservationRegistry).size() == 1
        context.getBeansOfType(ObservationHandler).size() == 1
        context.getBeansOfType(ObservationHandlerGroupingClass).size() == 2
        context.getBeansOfType(ObservationHandler)*.class*.simpleName.containsAll(['DefaultMeterObservationHandler'])
        context.getBeansOfType(ObservationHandlerGrouping).size() == 1
        context.getBeansOfType(ObservationFilter).size() == 1
    }

    void 'test trace and no metrics'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
        ).start()
        context.registerSingleton(tracerMocked)

        then:
        context.getBeansOfType(MeterRegistry).size() == 0
        context.getBeansOfType(Tracer).size() == 1
        context.getBeansOfType(Propagator).size() == 0
        context.getBeansOfType(ObservationHandler).size() == 1
        context.getBeansOfType(ObservationHandlerGroupingClass).size() == 2
        context.getBeansOfType(ObservationHandler)*.class*.simpleName.containsAll(['DefaultTracingObservationHandler'])
        context.getBeansOfType(ObservationFilter).size() == 1
        context.getBeansOfType(ObservationHandlerGrouping).size() == 1
        context.getBeansOfType(ObservationRegistry).size() == 1
    }

    void 'test trace and no metrics with propagator'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
        ).start()
        context.registerSingleton(tracerMocked)
        context.registerSingleton(propagator)

        then:
        context.getBeansOfType(MeterRegistry).size() == 0
        context.getBeansOfType(Tracer).size() == 1
        context.getBeansOfType(Propagator).size() == 1
        context.getBeansOfType(ObservationHandler).size() == 3
        context.getBeansOfType(ObservationHandlerGroupingClass).size() == 2
        context.getBeansOfType(ObservationHandler)*.class*.simpleName.containsAll(['PropagatingReceiverTracingObservationHandler', 'DefaultTracingObservationHandler', 'PropagatingSenderTracingObservationHandler'])
        context.getBeansOfType(ObservationFilter).size() == 1
        context.getBeansOfType(ObservationHandlerGrouping).size() == 1
        context.getBeansOfType(ObservationRegistry).size() == 1
    }

    void 'test metrics and trace with propagator'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
        ).start()
        context.registerSingleton(meterRegistryMocked)
        context.registerSingleton(tracerMocked)
        context.registerSingleton(propagator)

        then:
        def registry = context.getBeansOfType(ObservationRegistry)
        registry.size() == 1
        context.getBeansOfType(MeterRegistry).size() == 1
        context.getBeansOfType(Tracer).size() == 1
        context.getBeansOfType(Propagator).size() == 1
        context.getBeansOfType(ObservationHandlerGroupingClass).size() == 2
        context.getBeansOfType(ObservationHandlerGrouping).size() == 1
        context.getBeansOfType(ObservationHandler)*.class*.simpleName.containsAll(['TracingAwareMeterObservationHandler', 'PropagatingReceiverTracingObservationHandler', 'PropagatingSenderTracingObservationHandler', 'DefaultTracingObservationHandler'])
        context.getBeansOfType(ObservationFilter).size() == 1
        def handlers = context.getBeansOfType(ObservationHandler)
        // verify order of tracing handlers
        handlers[-1].class == DefaultTracingObservationHandler.class
        handlers[-2].class == PropagatingSenderTracingObservationHandler.class
        handlers[-3].class == PropagatingReceiverTracingObservationHandler.class
    }

}
