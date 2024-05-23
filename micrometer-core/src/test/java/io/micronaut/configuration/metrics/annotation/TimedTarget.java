package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Timed;
import io.micronaut.configuration.metrics.aggregator.MethodTaggerExample;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Singleton
class TimedTarget {

    @Timed("timed.test.max.blocking")
    Integer max(int a, int b) {
        return Math.max(a, b);
    }

    @Timed("timed.test.maxWithOptions.blocking")
    @MetricOptions(taggers = {MethodTaggerExample.class}, filterTaggers = true)
    Integer maxWithOptions(int a, int b) {
        return Math.max(a, b);
    }

    @Timed("timed.test.repeated1")
    @Timed("timed.test.repeated2")
    Integer repeated(int a, int b) {
        return Math.max(a, b);
    }

    @Timed("timed.test.max.blocking")
    Integer error(int a, int b) {
        throw new NumberFormatException("cannot");
    }

    @Timed(value = "timed.test.max.future", description = "some desc", extraTags = {"one", "two"})
    CompletableFuture<Integer> maxFuture(int a, int b) {
        return CompletableFuture.completedFuture(Math.max(a, b));
    }

    @Timed(value = "timed.test.max.single", description = "some desc", extraTags = {"one", "two"})
    Mono<Integer> maxSingle(int a, int b) {
        return Mono.just(Math.max(a, b));
    }

    @Timed(value = "timed.test.max.flowable", description = "some desc", extraTags = {"one", "two"})
    Flux<Integer> maxFlow(int a, int b) {
        return Flux.just(Math.max(a, b));
    }
}
