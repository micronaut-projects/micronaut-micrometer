package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Timed;
import io.reactivex.Flowable;
import io.reactivex.Single;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;

@Singleton
class TimedTarget {

    @Timed("timed.test.max.blocking")
    Integer max(int a, int b) {
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
        return CompletableFuture.completedFuture(
                Math.max(a, b)
        );
    }

    @Timed(value = "timed.test.max.single", description = "some desc", extraTags = {"one", "two"})
    Single<Integer> maxSingle(int a, int b) {
        return Single.just(
                Math.max(a, b)
        );
    }

    @Timed(value = "timed.test.max.flowable", description = "some desc", extraTags = {"one", "two"})
    Flowable<Integer> maxFlow(int a, int b) {
        return Flowable.just(
                Math.max(a, b)
        );
    }
}
