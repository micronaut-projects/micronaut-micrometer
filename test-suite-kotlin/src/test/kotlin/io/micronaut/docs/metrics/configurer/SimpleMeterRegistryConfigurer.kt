package io.micronaut.docs.metrics.configurer

// tag::imports[]
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.configuration.metrics.annotation.RequiresMetrics
import io.micronaut.core.annotation.Order
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "SimpleMeterRegistryConfigurerTest")
// tag::class[]
@Order(Int.MAX_VALUE)
@Singleton
@RequiresMetrics
class SimpleMeterRegistryConfigurer : MeterRegistryConfigurer<SimpleMeterRegistry> {

    override fun configure(meterRegistry: SimpleMeterRegistry) {
        meterRegistry.config().commonTags("key", "value")
    }

    override fun getType(): Class<SimpleMeterRegistry> = SimpleMeterRegistry::class.java
}
// end::class[]
