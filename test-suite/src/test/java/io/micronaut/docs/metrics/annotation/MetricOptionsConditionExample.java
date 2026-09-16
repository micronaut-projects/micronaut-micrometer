package io.micronaut.docs.metrics.annotation;

// tag::imports[]
import io.micrometer.core.annotation.Timed;
import io.micronaut.configuration.metrics.annotation.MetricOptions;
import jakarta.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class MetricOptionsConditionExample {

    @MetricOptions(
        // If condition is set, the metric will only be processed and published when it evaluates to true
        condition = "#{ env['property'] == 'true' }"
    )
    @Timed(value = "do_something_conditionally")
    public void doSomething() {
        // ...
    }
}
// end::class[]
