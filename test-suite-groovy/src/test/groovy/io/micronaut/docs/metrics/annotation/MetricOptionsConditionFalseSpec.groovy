package io.micronaut.docs.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "property", value = "false")
@MicronautTest
class MetricOptionsConditionFalseSpec extends Specification {

    @Inject
    MeterRegistry meterRegistry

    @Inject
    MetricOptionsConditionExample conditionExample

    void "metric is not published when the condition is false"() {
        when:
        conditionExample.doSomething()
        meterRegistry.get("do_something_conditionally").timer()

        then:
        thrown(MeterNotFoundException)
    }
}
