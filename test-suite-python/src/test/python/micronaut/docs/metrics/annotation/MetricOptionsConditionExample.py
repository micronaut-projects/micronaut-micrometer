# tag::imports[]
from jakarta.inject import Singleton
from micronaut.configuration.metrics.annotation import MetricOptions

try:
    from io.micrometer.core.annotation import Timed
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from micrometer.core.annotation import Timed
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
