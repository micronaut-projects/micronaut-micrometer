package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Uses evaluated expressions in the members of {@link Timed} and {@link Counted}.
 */
@Singleton
@Requires(property = "spec.name", value = "MetricExpressionSpec")
class MetricExpressionTarget {

    @Timed(value = "#{'timed.expr.' + 'name'}", description = "#{'timed ' + env['metric.expr.description']}")
    Integer timedScalar(int a, int b) {
        return Math.max(a, b);
    }

    @Timed(value = "timed.expr.tags", extraTags = {"literal", "value", "env", "#{env['metric.expr.tag']}"})
    Integer timedExtraTags(int a, int b) {
        return Math.max(a, b);
    }

    @Timed(value = "timed.expr.args", extraTags = {"arg", "#{#name}"})
    String timedArgument(String name) {
        return name;
    }

    @Timed("#{'timed.expr.repeated' + 1}")
    @Timed(value = "timed.expr.repeated2", extraTags = {"env", "#{env['metric.expr.tag']}"})
    Integer timedRepeated(int a, int b) {
        return Math.max(a, b);
    }

    @Counted(value = "#{'counted.expr.' + 'name'}", description = "#{'counted ' + env['metric.expr.description']}")
    Integer countedScalar(int a, int b) {
        return Math.max(a, b);
    }

    @Counted(value = "counted.expr.tags", extraTags = {"literal", "value", "env", "#{env['metric.expr.tag']}"})
    Integer countedExtraTags(int a, int b) {
        return Math.max(a, b);
    }
}
