package io.micronaut.docs.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "property", value = "true")
@MicronautTest
class MetricOptionsSpec extends Specification {

    @Inject
    MeterRegistry meterRegistry

    @Inject
    MetricOptionsFilterTaggersExample filterTaggersExample

    @Inject
    MetricOptionsConditionExample conditionExample

    void "only the selected taggers are applied"() {
        when:
        filterTaggersExample.doSomething()
        def timer = meterRegistry.get("do_something").tags("method", "doSomething").timer()

        then:
        timer.count() == 1
        timer.id.getTag("parameters") == null
    }

    void "metric is published when the condition is true"() {
        when:
        conditionExample.doSomething()
        def timer = meterRegistry.get("do_something_conditionally").timer()

        then:
        timer.count() == 1
    }
}
