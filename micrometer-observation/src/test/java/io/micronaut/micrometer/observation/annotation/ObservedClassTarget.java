package io.micronaut.micrometer.observation.annotation;

import io.micrometer.observation.annotation.Observed;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;

@Singleton
@Observed(name = "observed.class", lowCardinalityKeyValues = {"layer", "service"})
public class ObservedClassTarget {

    public String hello(String name) {
        return "Hello " + name;
    }

    public CompletableFuture<String> helloAsync(String name) {
        return CompletableFuture.completedFuture("Hello " + name);
    }

    @Observed(name = "observed.method", contextualName = "methodLevel")
    public String overridden() {
        return "overridden";
    }
}
