package io.micronaut.micrometer.observation.annotation

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer

import java.util.function.Consumer
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions


class ObservationAnnotationSpec extends Specification {

    void "test observation annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, [
                'spec.name': 'ObservationAnnotationSpec'
        ])
        ObservedTarget tt = ctx.getBean(ObservedTarget)
        TestObservationRegistry registry = ctx.getBean(ObservationRegistry) as TestObservationRegistry

        when:
        tt.max(4, 10)

        then:
        TestObservationRegistryAssert.assertThat(registry)
                .doesNotHaveAnyRemainingCurrentObservation()
                .hasObservationWithNameEqualTo("observed.test.max.blocking")
                .that()
                .hasContextualNameEqualTo("ObservedTarget#max")
                .hasLowCardinalityKeyValue("class", "ObservedTarget")
                .hasLowCardinalityKeyValue("method", "max")
                .hasBeenStarted()
                .hasBeenStopped()


        when:
        tt.maxFuture(4, 10).get()
        PollingConditions conditions = new PollingConditions()

        then:
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(registry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasObservationWithNameEqualTo("observed.test.max.future")
                    .that()
                    .hasContextualNameEqualTo("contextualNameTest")
                    .hasLowCardinalityKeyValue("class", "ObservedTarget")
                    .hasLowCardinalityKeyValue("method", "maxFuture")
                    .hasLowCardinalityKeyValue("one", "two")
                    .hasBeenStarted()
                    .hasBeenStopped()
        }

        when:
        tt.maxSingle(4, 10).subscribe( { o -> o} as Consumer)

        then:
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(registry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasObservationWithNameEqualTo("observed.test.max.single")
                    .that()
                    .hasContextualNameEqualTo("contextualNameTest")
                    .hasLowCardinalityKeyValue("class", "ObservedTarget")
                    .hasLowCardinalityKeyValue("method", "maxSingle")
                    .hasLowCardinalityKeyValue("one", "two")
                    .hasBeenStarted()
                    .hasBeenStopped()
        }

        when:
        tt.maxFlow(4, 10).collectList().subscribe( { o -> o[0]} as Consumer)

        then:
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(registry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasObservationWithNameEqualTo("observed.test.max.flowable")
                    .that()
                    .hasContextualNameEqualTo("contextualNameTest")
                    .hasLowCardinalityKeyValue("class", "ObservedTarget")
                    .hasLowCardinalityKeyValue("method", "maxFlow")
                    .hasLowCardinalityKeyValue("one", "two")
                    .hasBeenStarted()
                    .hasBeenStopped()
        }

        cleanup:
        ctx.close()
    }

    @Factory
    @Requires(property = "spec.name", value = "ObservationAnnotationSpec")
    static class DefaultFactory {

        @Singleton
        @Primary
        ObservationRegistry observationRegistry() {
            return TestObservationRegistry.create()
        }

    }
}
