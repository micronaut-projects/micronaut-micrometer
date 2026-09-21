from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MetricOptionsConditionExample import MetricOptionsConditionExample
from .MetricOptionsFilterTaggersExample import MetricOptionsFilterTaggersExample


@Property(name="property", value="true")
@MicronautTest
class MetricOptionsTest:
    meterRegistry: Annotated[MeterRegistry, Inject]
    filterTaggersExample: Annotated[MetricOptionsFilterTaggersExample, Inject]
    conditionExample: Annotated[MetricOptionsConditionExample, Inject]

    @Test
    def test_only_the_selected_taggers_are_applied(self):
        self.filterTaggersExample.do_something()

        timer = self.meterRegistry.get("do_something").tags("method", "do_something").timer()
        assert timer.count() == 1
        assert timer.getId().getTag("parameters") is None

    @Test
    def test_metric_is_published_when_the_condition_is_true(self):
        self.conditionExample.do_something()

        timer = self.meterRegistry.get("do_something_conditionally").timer()
        assert timer.count() == 1
