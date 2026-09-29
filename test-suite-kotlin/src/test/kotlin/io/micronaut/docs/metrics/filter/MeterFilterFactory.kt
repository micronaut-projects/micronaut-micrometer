package io.micronaut.docs.metrics.filter

// tag::imports[]
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.config.MeterFilter
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "MeterFilterFactoryTest")
// tag::class[]
@Factory
class MeterFilterFactory {

    /**
     * Add global tags to all metrics.
     *
     * @return meter filter
     */
    @Bean
    @Singleton
    fun addCommonTagFilter(): MeterFilter =
        MeterFilter.commonTags(listOf(Tag.of("scope", "demo")))

    /**
     * Rename a tag key for every metric beginning with a given prefix.
     *
     * This will rename the metric name http.server.requests tag value called `method` to `httpmethod`
     *
     * OLD: http.server.requests ['method':'GET", ...]
     * NEW: http.server.requests ['httpmethod':'GET", ...]
     *
     * @return meter filter
     */
    @Bean
    @Singleton
    fun renameFilter(): MeterFilter =
        MeterFilter.renameTag("http.server.requests", "method", "httpmethod")
}
// end::class[]
