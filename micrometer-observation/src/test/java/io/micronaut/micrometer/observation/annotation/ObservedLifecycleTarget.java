package io.micronaut.micrometer.observation.annotation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.Observed;
import io.micronaut.micrometer.observation.utils.ObservedReactorPropagation;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class ObservedLifecycleTarget {

    @Observed(name = "observed.lifecycle.mono")
    Mono<Integer> mono(Mono<Integer> source) {
        return source;
    }

    @Observed(name = "observed.lifecycle.flux")
    Flux<Integer> flux(Flux<Integer> source) {
        return source;
    }

    @Observed(name = "observed.lifecycle.sync.throw")
    Integer syncThrow() {
        throw new IllegalStateException("sync");
    }

    @Observed(name = "observed.lifecycle.mono.sync.throw")
    Mono<Integer> monoSyncThrow() {
        throw new IllegalStateException("sync");
    }

    @Observed(name = "#{ 'observed.lifecycle.concurrent.' + ctx[T(io.micronaut.micrometer.observation.annotation.FirstCallBarrier)].arrive(#arg) }",
        lowCardinalityKeyValues = {"arg", "#{ #arg }"})
    String concurrentExpression(String arg) {
        return arg;
    }

    @Observed(name = "observed.lifecycle.future.sync.error")
    CompletableFuture<Integer> futureSyncError() {
        throw new AssertionError("direct");
    }

    @Observed(name = "observed.lifecycle.future.sync.throw")
    CompletableFuture<Integer> futureSyncThrow() {
        throw new IllegalStateException("sync");
    }

    @Observed(name = "observed.lifecycle.future.async.error")
    CompletableFuture<Integer> futureAsyncError() {
        return CompletableFuture.<Integer>supplyAsync(() -> {
            throw new IllegalStateException("async");
        }).thenApply(i -> i + 1);
    }

    @Observed(name = "observed.lifecycle.context")
    Mono<Optional<Observation>> observationFromContext() {
        return Mono.deferContextual(ctx -> Mono.just(Optional.ofNullable(ObservedReactorPropagation.currentObservation(ctx))));
    }
}
