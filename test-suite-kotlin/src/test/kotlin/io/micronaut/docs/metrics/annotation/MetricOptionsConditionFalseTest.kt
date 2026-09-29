package io.micronaut.docs.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@Property(name = "property", value = "false")
@MicronautTest
class MetricOptionsConditionFalseTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Inject
    lateinit var conditionExample: MetricOptionsConditionExample

    @Test
    fun metricIsNotPublishedWhenTheConditionIsFalse() {
        conditionExample.doSomething()

        assertThrows(MeterNotFoundException::class.java) { meterRegistry.get("do_something_conditionally").timer() }
    }
}
