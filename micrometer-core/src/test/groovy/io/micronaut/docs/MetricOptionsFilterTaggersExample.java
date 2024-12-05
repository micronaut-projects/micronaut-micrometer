package io.micronaut.docs;

import io.micrometer.core.annotation.Timed;
import io.micronaut.configuration.metrics.annotation.MetricOptions;
import jakarta.inject.Singleton;

@Singleton
public class MetricOptionsFilterTaggersExample {

    @MetricOptions(
        filterTaggers = true, // Specify that not all taggers should be applied
        taggers = {MethodNameTagger.class} // Specific taggers to apply
    )
    @Timed(value = "do_something")
    public void doSomething() {
        // ...
    }
}
