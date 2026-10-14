package io.micronaut.configuration.metrics.annotation;

import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Singleton
class MeterTagTarget implements MeterTagApi {

    @Timed("metertag.timed.parameters")
    @MeterTag(key = "size", expression = "#{ #name.length() + 1 }")
    Integer parameters(@MeterTag("tenant") String tenant,
                       @MeterTag(key = "upper", resolver = UpperCaseResolver.class) String upper,
                       @MeterTag(key = "length", resolver = LengthResolver.class) String length,
                       @MeterTag(key = "zone", expression = "#{ 'zone-' + this.zone() }") String name,
                       @MeterTag("missing") Object missing) {
        return 1;
    }

    String zone() {
        return "a";
    }

    @Timed("metertag.timed.expression.resolver")
    Integer expressionResolver(@MeterTag(key = "reversed", expression = "reverse") String value) {
        return 1;
    }

    @Timed("metertag.timed.repeated")
    Integer repeated(@MeterTag("first") @MeterTag(key = "second", resolver = UpperCaseResolver.class) @MeterTag(key = "first", expression = "#{ 'ignored' }") String value) {
        return 1;
    }

    @Timed("metertag.timed.result")
    @MeterTag("result")
    Integer result(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
        return value;
    }

    @Counted("metertag.counted")
    @MeterTag(key = "returned", resolver = UpperCaseResolver.class)
    String counted(@MeterTag("tenant") String tenant) {
        return "ok-" + tenant;
    }

    @Counted("metertag.counted.mono")
    @MeterTag("returned")
    Mono<String> countedMono(@MeterTag("tenant") String tenant) {
        return Mono.just("mono-" + tenant);
    }

    @Timed("metertag.timed.future")
    @MeterTag("result")
    CompletableFuture<String> timedFuture(@MeterTag("tenant") String tenant) {
        return CompletableFuture.supplyAsync(() -> "future-" + tenant);
    }

    @Timed(value = "metertag.long.task", longTask = true)
    Integer longTask(@MeterTag("tenant") String tenant, CountDownLatch started, CountDownLatch release) throws InterruptedException {
        started.countDown();
        release.await(10, TimeUnit.SECONDS);
        return 1;
    }

    @Override
    @Timed("metertag.timed.interface")
    public Integer fromInterface(String region) {
        return 1;
    }

    @Singleton
    static class UpperCaseResolver implements ValueResolver {
        @Override
        public String resolve(Object parameter) {
            return String.valueOf(parameter).toUpperCase();
        }
    }

    public static class LengthResolver implements ValueResolver {
        @Override
        public String resolve(Object parameter) {
            return String.valueOf(String.valueOf(parameter).length());
        }
    }
}
