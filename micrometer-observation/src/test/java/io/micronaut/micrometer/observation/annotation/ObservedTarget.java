package io.micronaut.micrometer.observation.annotation;

import io.micrometer.core.annotation.Counted;
import io.micrometer.observation.annotation.Observed;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Singleton
public class ObservedTarget {

    @Observed(name = "observed.test.max.blocking")
    Integer max(int a, int b) {
        return Math.max(a, b);
    }

    @Observed(name = "observed.test.max.blocking")
    Integer error(int a, int b) {
        throw new NumberFormatException("cannot");
    }

    @Observed(name = "observed.test.max.future", contextualName = "contextualNameTest", lowCardinalityKeyValues = {"one", "two"})
    CompletableFuture<Integer> maxFuture(int a, int b) {
        return CompletableFuture.completedFuture(Math.max(a, b));
    }

    @Observed(name = "observed.test.max.single", contextualName = "contextualNameTest", lowCardinalityKeyValues = {"one", "two"})
    Mono<Integer> maxSingle(int a, int b) {
        return Mono.just(Math.max(a, b));
    }

    @Observed(name = "observed.test.max.flowable", contextualName = "contextualNameTest", lowCardinalityKeyValues = {"one", "two"})
    Flux<Integer> maxFlow(int a, int b) {
        return Flux.just(Math.max(a, b));
    }
}
