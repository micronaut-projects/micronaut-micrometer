package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Timed;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Singleton
class TimedLifecycleTarget {

    @Timed("timed.lifecycle.flux.error")
    Flux<Integer> fluxError() {
        return Flux.concat(Flux.just(1, 2), Flux.error(new IllegalStateException("boom")));
    }

    @Timed("timed.lifecycle.flux.slow")
    Flux<Integer> fluxSlow(int count, long delayMillis) {
        return Flux.range(1, count).delayElements(Duration.ofMillis(delayMillis));
    }

    @Timed("timed.lifecycle.flux.never")
    Flux<Integer> fluxNever() {
        return Flux.never();
    }

    @Timed("timed.lifecycle.mono")
    Mono<Integer> mono() {
        return Mono.just(1);
    }

    @Timed("timed.lifecycle.mono.delayed")
    Mono<Long> monoDelayed(long delayMillis) {
        return Mono.delay(Duration.ofMillis(delayMillis));
    }

    @Timed("timed.lifecycle.mono.sync.throw")
    Mono<Integer> monoSyncThrow() {
        throw new IllegalStateException("sync");
    }

    @Timed("timed.lifecycle.future.sync.throw")
    CompletableFuture<Integer> futureSyncThrow() {
        throw new IllegalStateException("sync");
    }

    @Timed("timed.lifecycle.future.async.error")
    CompletableFuture<Integer> futureAsyncError() {
        return CompletableFuture.<Integer>supplyAsync(() -> {
            throw new IllegalStateException("async");
        }).thenApply(i -> i + 1);
    }

    @Timed(value = "timed.lifecycle.long.task", longTask = true, extraTags = {"kind", "blocking"})
    Integer longTask(CountDownLatch started, CountDownLatch release) throws InterruptedException {
        started.countDown();
        release.await(10, TimeUnit.SECONDS);
        return 1;
    }

    @Timed(value = "timed.lifecycle.long.task.mono", longTask = true)
    Mono<Integer> longTaskMono(Mono<Integer> source) {
        return source;
    }

    @Timed(value = "timed.lifecycle.long.task.future", longTask = true)
    CompletableFuture<Integer> longTaskFuture(CompletableFuture<Integer> source) {
        return source;
    }

    @Timed(value = "timed.lifecycle.long.task.future.error", longTask = true)
    CompletableFuture<Integer> longTaskFutureError() {
        throw new AssertionError("direct");
    }

    @Timed("timed.lifecycle.both.timer")
    @Timed(value = "timed.lifecycle.both.long.task", longTask = true)
    Integer timerAndLongTask() {
        return 1;
    }
}
