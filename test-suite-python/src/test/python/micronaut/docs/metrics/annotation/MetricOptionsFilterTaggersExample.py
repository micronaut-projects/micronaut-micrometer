# tag::imports[]
from io.micrometer.core.annotation import Timed
from jakarta.inject import Singleton
from micronaut.configuration.metrics.annotation import MetricOptions

from .MethodNameTagger import MethodNameTagger
# end::imports[]


# tag::class[]
@Singleton
class MetricOptionsFilterTaggersExample:

    @MetricOptions(
        filterTaggers=True,  # Specify that not all taggers should be applied
        taggers=[MethodNameTagger]  # Specific taggers to apply
    )
    @Timed(value="do_something")
    def do_something(self) -> None:
        pass
# end::class[]
