package io.micronaut.docs.metrics.configurer

// tag::imports[]
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.configuration.metrics.annotation.RequiresMetrics
import io.micronaut.core.annotation.Order
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "SimpleMeterRegistryConfigurerSpec")
// tag::class[]
@Order(Integer.MAX_VALUE)
@Singleton
@RequiresMetrics
class SimpleMeterRegistryConfigurer implements MeterRegistryConfigurer<SimpleMeterRegistry> {

    @Override
    void configure(SimpleMeterRegistry meterRegistry) {
        meterRegistry.config().commonTags("key", "value")
    }

    @Override
    Class<SimpleMeterRegistry> getType() {
        SimpleMeterRegistry
    }
}
// end::class[]
