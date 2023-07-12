package io.micronaut.micrometer.observation

import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.tracing.handler.TracingObservationHandler
import spock.lang.Specification

class ObservationHandlerGroupingSpec extends Specification {

    public ObservationHandler observationHandler = Mock(ObservationHandler)
    public ObservationHandler tracingObservationHandler = Mock(TracingObservationHandler)
    public ObservationHandler tracingObservationHandler2 = Mock(TracingObservationHandler)

    void 'custom ObservationHandler'() {
        def registry = TestObservationRegistry.create()
        ObservationRegistry observationRegistry = registry

        def o = new ObservationHandlerGrouping(List.of())

        when:
        o.apply(List.of(observationHandler), observationRegistry.observationConfig())

        then:
        List<ObservationHandler> handlers = registry.observationConfig().getProperties()["observationHandlers"]
        handlers.size() == 2
        handlers.find(x-> x == observationHandler)
        !handlers.find(x -> x.getClass() == ObservationHandler.FirstMatchingCompositeObservationHandler.class)
    }

    void 'check if TracingObservationHandler is grouped'() {
        def registry = TestObservationRegistry.create()
        ObservationRegistry observationRegistry = registry

        def o = new ObservationHandlerGrouping(List.of(TracingObservationHandler.class))

        when:
        o.apply(List.of(tracingObservationHandler, tracingObservationHandler2), observationRegistry.observationConfig())

        then:
        List<ObservationHandler> handlers = registry.observationConfig().getProperties()["observationHandlers"]
        handlers.size() == 2
        handlers.find(x -> x.getClass() == ObservationHandler.FirstMatchingCompositeObservationHandler.class)
    }

    void 'check if TracingObservationHandler is grouped and custom one is registered separately'() {
        def registry = TestObservationRegistry.create()
        ObservationRegistry observationRegistry = registry

        def o = new ObservationHandlerGrouping(List.of(TracingObservationHandler.class))

        when:
        o.apply(List.of(observationHandler, tracingObservationHandler2, tracingObservationHandler), observationRegistry.observationConfig())

        then:
        List<ObservationHandler> handlers = registry.observationConfig().getProperties()["observationHandlers"]
        handlers.size() == 3
        handlers.find(x -> x.getClass() == ObservationHandler.FirstMatchingCompositeObservationHandler.class)
        handlers.find(x-> x == observationHandler)
    }
}
