package io.micronaut.docs.metrics.expression

import io.micrometer.core.annotation.Counted
import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@Singleton
@Requires(property = "spec.name", value = "MetricExpressionTest")
open class MetricExpressionTarget {

    @Timed(value = "#{'kotlin.timed.expr.' + 'name'}", description = "#{'timed ' + env['metric.expr.description']}")
    open fun timedScalar(): Int = 1

    @Timed(value = "kotlin.timed.expr.tags", extraTags = ["literal", "value", "env", "#{env['metric.expr.tag']}"])
    open fun timedExtraTags(): Int = 1

    @Timed(value = "kotlin.timed.expr.args", extraTags = ["arg", "#{#name}"])
    open fun timedArgument(name: String): String = name

    @Timed("#{'kotlin.timed.expr.repeated' + 1}")
    @Timed(value = "#{'kotlin.timed.expr.repeated' + 2}", description = "#{'repeated ' + env['metric.expr.description']}")
    open fun timedRepeated(): Int = 1

    @Counted(value = "#{'kotlin.counted.expr.' + 'name'}", description = "#{'counted ' + env['metric.expr.description']}")
    open fun countedScalar(): Int = 1

    @Counted(value = "kotlin.counted.expr.tags", extraTags = ["literal", "value", "env", "#{env['metric.expr.tag']}"])
    open fun countedExtraTags(): Int = 1
}
