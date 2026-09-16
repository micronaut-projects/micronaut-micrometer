package io.micronaut.docs.metrics.annotation

// tag::imports[]
import io.micrometer.core.annotation.Timed
import io.micronaut.configuration.metrics.annotation.MetricOptions
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
open class MetricOptionsFilterTaggersExample {

    @MetricOptions(
        filterTaggers = true, // Specify that not all taggers should be applied
        taggers = [MethodNameTagger::class] // Specific taggers to apply
    )
    @Timed(value = "do_something")
    open fun doSomething() {
        // ...
    }
}
// end::class[]
