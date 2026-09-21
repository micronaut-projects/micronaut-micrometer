from micronaut.context.annotation import Requires

# tag::imports[]
from io.micrometer.core.instrument.simple import SimpleMeterRegistry
from jakarta.inject import Singleton
from micronaut.configuration.metrics.aggregator import MeterRegistryConfigurer
from micronaut.configuration.metrics.annotation import RequiresMetrics
from micronaut.core.annotation import Order
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
