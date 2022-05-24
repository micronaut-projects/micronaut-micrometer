package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Singleton
public class CountedTarget {

    @Counted("counted.test.max.blocking")
    Integer max(int a, int b) {
        return Math.max(a, b);
    }

    @Counted("counted.test.max.blocking")
    Integer error(int a, int b) {
        throw new NumberFormatException("cannot");
    }

    @Counted(value = "counted.test.max.future", description = "some desc", extraTags = {"one", "two"})
    CompletableFuture<Integer> maxFuture(int a, int b) {
        return CompletableFuture.completedFuture(Math.max(a, b));
    }

    @Counted(value = "counted.test.max.single", description = "some desc", extraTags = {"one", "two"})
    Mono<Integer> maxSingle(int a, int b) {
        return Mono.just(Math.max(a, b));
    }

    @Counted(value = "counted.test.max.flowable", description = "some desc", extraTags = {"one", "two"})
    Flux<Integer> maxFlow(int a, int b) {
        return Flux.just(Math.max(a, b));
    }
}
