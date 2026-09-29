package io.micronaut.docs.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@Property(name = "property", value = "true")
@MicronautTest
class MetricOptionsTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Inject
    lateinit var filterTaggersExample: MetricOptionsFilterTaggersExample

    @Inject
    lateinit var conditionExample: MetricOptionsConditionExample

    @Test
    fun onlyTheSelectedTaggersAreApplied() {
        filterTaggersExample.doSomething()

        val timer = meterRegistry.get("do_something").tags("method", "doSomething").timer()
        assertEquals(1, timer.count())
        assertNull(timer.id.getTag("parameters"))
    }

    @Test
    fun metricIsPublishedWhenTheConditionIsTrue() {
        conditionExample.doSomething()

        val timer = meterRegistry.get("do_something_conditionally").timer()
        assertEquals(1, timer.count())
    }
}
