package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import io.reactivex.Flowable;
import io.reactivex.Single;

import javax.inject.Singleton;
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
        return CompletableFuture.completedFuture(
                Math.max(a, b)
        );
    }

    @Counted(value = "counted.test.max.single", description = "some desc", extraTags = {"one", "two"})
    Single<Integer> maxSingle(int a, int b) {
        return Single.just(
                Math.max(a, b)
        );
    }

    @Counted(value = "counted.test.max.flowable", description = "some desc", extraTags = {"one", "two"})
    Flowable<Integer> maxFlow(int a, int b) {
        return Flowable.just(
                Math.max(a, b)
        );
    }
}
