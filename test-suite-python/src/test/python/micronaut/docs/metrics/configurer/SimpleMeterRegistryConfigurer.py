from micronaut.context.annotation import Requires

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.configuration.metrics.aggregator import MeterRegistryConfigurer
from micronaut.configuration.metrics.annotation import RequiresMetrics
from micronaut.core.annotation import Order

try:
    from io.micrometer.core.instrument.simple import SimpleMeterRegistry
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from micrometer.core.instrument.simple import SimpleMeterRegistry
# end::imports[]


@Requires(property="spec.name", value="SimpleMeterRegistryConfigurerTest")
# tag::class[]
@Order(2147483647)
@Singleton
@RequiresMetrics
class SimpleMeterRegistryConfigurer(MeterRegistryConfigurer[SimpleMeterRegistry]):

    def configure(self, meterRegistry: SimpleMeterRegistry) -> None:
        meterRegistry.config().commonTags("key", "value")

    def getType(self) -> type[SimpleMeterRegistry]:
        return SimpleMeterRegistry
# end::class[]
