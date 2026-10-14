package io.micronaut.docs.metrics.expression

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "spec.name", value = "MetricExpressionTest")
@Property(name = "metric.expr.description", value = "description")
@Property(name = "metric.expr.tag", value = "from-env")
class MetricExpressionTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Inject
    lateinit var target: MetricExpressionTarget

    @Test
    fun timedEvaluatesExpressionsInTheNameAndTheDescription() {
        target.timedScalar()
        val timer = meterRegistry.get("kotlin.timed.expr.name").timer()
        assertEquals(1, timer.count())
        assertEquals("timed description", timer.id.description)
    }

    @Test
    fun timedEvaluatesExpressionsInExtraTags() {
        target.timedExtraTags()
        val timer = meterRegistry.get("kotlin.timed.expr.tags").timer()
        assertEquals(1, timer.count())
        assertEquals("value", timer.id.getTag("literal"))
        assertEquals("from-env", timer.id.getTag("env"))
    }

    @Test
    fun timedEvaluatesExpressionsThatReferenceMethodArgumentsInExtraTags() {
        target.timedArgument("first")
        target.timedArgument("second")
        assertEquals(1, meterRegistry.get("kotlin.timed.expr.args").tag("arg", "first").timer().count())
        assertEquals(1, meterRegistry.get("kotlin.timed.expr.args").tag("arg", "second").timer().count())
    }

    @Test
    fun repeatedTimedEvaluatesExpressionsInEachAnnotation() {
        target.timedRepeated()
        assertEquals(1, meterRegistry.get("kotlin.timed.expr.repeated1").timer().count())
        val repeated2 = meterRegistry.get("kotlin.timed.expr.repeated2").timer()
        assertEquals(1, repeated2.count())
        assertEquals("repeated description", repeated2.id.description)
    }

    @Test
    fun countedEvaluatesExpressionsInTheNameAndTheDescription() {
        target.countedScalar()
        val counter = meterRegistry.get("kotlin.counted.expr.name").counter()
        assertEquals(1.0, counter.count())
        assertEquals("counted description", counter.id.description)
    }

    @Test
    fun countedEvaluatesExpressionsInExtraTags() {
        target.countedExtraTags()
        val counter = meterRegistry.get("kotlin.counted.expr.tags").counter()
        assertEquals(1.0, counter.count())
        assertEquals("value", counter.id.getTag("literal"))
        assertEquals("from-env", counter.id.getTag("env"))
    }
}
