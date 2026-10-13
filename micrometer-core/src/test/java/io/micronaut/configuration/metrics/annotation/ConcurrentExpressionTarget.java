package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Singleton;

@Singleton
class ConcurrentExpressionTarget {

    @Timed(value = "timed.concurrent.expression",
        extraTags = {"arg", "#{ ctx[T(io.micronaut.configuration.metrics.annotation.FirstCallBarrier)].arrive(#arg) }"})
    String timed(String arg) {
        return arg;
    }

    @Counted(value = "counted.concurrent.expression",
        extraTags = {"arg", "#{ ctx[T(io.micronaut.configuration.metrics.annotation.FirstCallBarrier)].arrive(#arg) }"})
    String counted(String arg) {
        return arg;
    }
}
