from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry
from io.micrometer.core.instrument.simple import SimpleMeterRegistry
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="SimpleMeterRegistryConfigurerTest")
@MicronautTest
class SimpleMeterRegistryConfigurerTest:
    meterRegistry: Annotated[MeterRegistry, Inject]

    @Test
    def test_the_configurer_is_applied_to_the_simple_meter_registry(self):
        # the SimpleMeterRegistry is the default registry of the composite when no other registry is configured
        simpleMeterRegistry = None
        for registry in self.meterRegistry.getRegistries():
            if isinstance(registry, SimpleMeterRegistry):
                simpleMeterRegistry = registry
        assert simpleMeterRegistry is not None

        self.meterRegistry.counter("configurer.test").increment()

        counter = simpleMeterRegistry.get("configurer.test").tags("key", "value").counter()
        assert counter.count() == 1.0
