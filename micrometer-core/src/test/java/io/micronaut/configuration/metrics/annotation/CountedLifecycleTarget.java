package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Singleton
class CountedLifecycleTarget {

    @Counted(value = "counted.lifecycle.failures.mono", recordFailuresOnly = true)
    Mono<Integer> failuresOnlyMono(boolean fail) {
        return fail ? Mono.error(new IllegalStateException("boom")) : Mono.just(1);
    }

    @Counted(value = "counted.lifecycle.failures.flux", recordFailuresOnly = true)
    Flux<Integer> failuresOnlyFlux(boolean fail) {
        return fail ? Flux.concat(Flux.just(1), Flux.error(new IllegalStateException("boom"))) : Flux.just(1, 2);
    }

    @Counted(value = "counted.lifecycle.failures.future", recordFailuresOnly = true)
    CompletableFuture<Integer> failuresOnlyFuture(boolean fail) {
        return CompletableFuture.supplyAsync(() -> {
            if (fail) {
                throw new IllegalStateException("async");
            }
            return 1;
        });
    }

    @Counted(value = "counted.lifecycle.failures.sync", recordFailuresOnly = true)
    Integer failuresOnlySync(boolean fail) {
        if (fail) {
            throw new IllegalStateException("sync");
        }
        return 1;
    }

    @Counted("counted.lifecycle.future.sync.error")
    CompletableFuture<Integer> futureSyncError() {
        throw new AssertionError("direct");
    }

    @Counted("counted.lifecycle.future.async.error")
    CompletableFuture<Integer> futureAsyncError() {
        return CompletableFuture.<Integer>supplyAsync(() -> {
            throw new IllegalStateException("async");
        }).thenApply(i -> i + 1);
    }

    @Counted("counted.lifecycle.mono.sync.throw")
    Mono<Integer> monoSyncThrow() {
        throw new IllegalStateException("sync");
    }

    @Counted("counted.lifecycle.mono")
    Mono<Integer> mono() {
        return Mono.just(1);
    }

    @Counted("counted.lifecycle.flux.error")
    Flux<Integer> fluxError() {
        return Flux.concat(Flux.just(1, 2), Flux.error(new IllegalStateException("boom")));
    }
}
