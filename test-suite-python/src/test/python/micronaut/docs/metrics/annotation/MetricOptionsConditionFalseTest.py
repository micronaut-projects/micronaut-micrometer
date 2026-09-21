from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry
from io.micrometer.core.instrument.search import MeterNotFoundException
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MetricOptionsConditionExample import MetricOptionsConditionExample


@Property(name="property", value="false")
@MicronautTest
class MetricOptionsConditionFalseTest:
    meterRegistry: Annotated[MeterRegistry, Inject]
    conditionExample: Annotated[MetricOptionsConditionExample, Inject]

    @Test
    def test_metric_is_not_published_when_the_condition_is_false(self):
        self.conditionExample.do_something()

        try:
            self.meterRegistry.get("do_something_conditionally").timer()
            assert False, "the meter must not be published"
        except MeterNotFoundException:
            pass
