package io.micronaut.docs.metrics.configurer;

// tag::imports[]
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "SimpleMeterRegistryConfigurerTest")
// tag::class[]
@Order(Integer.MAX_VALUE)
@Singleton
@RequiresMetrics
public class SimpleMeterRegistryConfigurer implements MeterRegistryConfigurer<SimpleMeterRegistry> {

    @Override
    public void configure(SimpleMeterRegistry meterRegistry) {
        meterRegistry.config().commonTags("key", "value");
    }

    @Override
    public Class<SimpleMeterRegistry> getType() {
        return SimpleMeterRegistry.class;
    }
}
// end::class[]
