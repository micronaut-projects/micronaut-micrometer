# tag::imports[]
from io.micrometer.core.annotation import Timed
from jakarta.inject import Singleton
from micronaut.configuration.metrics.annotation import MetricOptions
# end::imports[]


# tag::class[]
@Singleton
class MetricOptionsConditionExample:

    @MetricOptions(
        # If condition is set, the metric will only be processed and published when it evaluates to true
        condition="#{ env['property'] == 'true' }"
    )
    @Timed(value="do_something_conditionally")
    def do_something(self) -> None:
        pass
# end::class[]
