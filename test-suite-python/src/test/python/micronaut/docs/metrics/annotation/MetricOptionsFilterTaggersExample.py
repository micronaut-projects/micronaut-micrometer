# tag::imports[]
from jakarta.inject import Singleton
from micronaut.configuration.metrics.annotation import MetricOptions

from .MethodNameTagger import MethodNameTagger

# TODO(python): a class imported inside the `try:` block below is emitted without its package in
# class-valued annotation members (`taggers=[MethodNameTagger]`), so the tagger import stays above it
try:
    from io.micrometer.core.annotation import Timed
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from micrometer.core.annotation import Timed
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
