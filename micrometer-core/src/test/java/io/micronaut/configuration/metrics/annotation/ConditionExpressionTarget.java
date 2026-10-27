package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Singleton;

@Singleton
class ConditionExpressionTarget {

    @Timed(value = "timed.condition.expression",
        extraTags = {"probe", "#{ ctx[T(io.micronaut.configuration.metrics.annotation.MetricConditionProbe)].tag() }"})
    @MetricOptions(condition = "#{ ctx[T(io.micronaut.configuration.metrics.annotation.MetricConditionProbe)].isEnabled() }")
    String timed() {
        return "timed-result";
    }

    @Counted(value = "counted.condition.expression",
        extraTags = {"probe", "#{ ctx[T(io.micronaut.configuration.metrics.annotation.MetricConditionProbe)].tag() }"})
    @MetricOptions(condition = "#{ ctx[T(io.micronaut.configuration.metrics.annotation.MetricConditionProbe)].isEnabled() }")
    String counted() {
        return "counted-result";
    }
}
