package io.micronaut.docs.metrics.configurer

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "SimpleMeterRegistryConfigurerTest")
@MicronautTest
class SimpleMeterRegistryConfigurerTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Test
    fun theConfigurerIsAppliedToTheSimpleMeterRegistry() {
        // the SimpleMeterRegistry is the default registry of the composite when no other registry is configured
        val composite = assertInstanceOf(CompositeMeterRegistry::class.java, meterRegistry)
        val simpleMeterRegistry = composite.registries.filterIsInstance<SimpleMeterRegistry>().first()

        meterRegistry.counter("configurer.test").increment()

        val counter = simpleMeterRegistry.get("configurer.test").tags("key", "value").counter()
        assertEquals(1.0, counter.count())
    }
}
