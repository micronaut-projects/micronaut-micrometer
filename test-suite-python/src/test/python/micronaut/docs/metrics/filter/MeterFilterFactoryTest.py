from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

try:
    from io.micrometer.core.instrument import MeterRegistry
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from micrometer.core.instrument import MeterRegistry


@Property(name="spec.name", value="MeterFilterFactoryTest")
@MicronautTest
class MeterFilterFactoryTest:
    meterRegistry: Annotated[MeterRegistry, Inject]

    @Test
    def test_the_common_tag_is_added_to_every_meter(self):
        counter = self.meterRegistry.counter("filter.test")

        assert counter.getId().getTag("scope") == "demo"

    @Test
    def test_the_method_tag_of_the_server_requests_meter_is_renamed(self):
        timer = self.meterRegistry.timer("http.server.requests", "method", "GET")

        assert timer.getId().getTag("httpmethod") == "GET"
        assert timer.getId().getTag("method") is None
