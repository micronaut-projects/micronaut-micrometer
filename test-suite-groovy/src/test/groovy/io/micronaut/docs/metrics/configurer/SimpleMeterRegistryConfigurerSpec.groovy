package io.micronaut.docs.metrics.configurer

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "SimpleMeterRegistryConfigurerSpec")
@MicronautTest
class SimpleMeterRegistryConfigurerSpec extends Specification {

    @Inject
    MeterRegistry meterRegistry

    void "the configurer is applied to the simple meter registry"() {
        given: "the SimpleMeterRegistry is the default registry of the composite when no other registry is configured"
        def simpleMeterRegistry = (meterRegistry as CompositeMeterRegistry).registries.find { it instanceof SimpleMeterRegistry }

        when:
        meterRegistry.counter("configurer.test").increment()
        def counter = simpleMeterRegistry.get("configurer.test").tags("key", "value").counter()

        then:
        counter.count() == 1.0d
    }
}
