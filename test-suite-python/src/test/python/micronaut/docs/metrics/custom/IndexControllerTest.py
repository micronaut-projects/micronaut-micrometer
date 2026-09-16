from typing import Annotated

from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

try:
    from io.micrometer.core.instrument import MeterRegistry
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from micrometer.core.instrument import MeterRegistry


@MicronautTest
class IndexControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    meterRegistry: Annotated[MeterRegistry, Inject]

    @Test
    def test_the_custom_counter_is_incremented_on_every_request(self):
        assert self.client.toBlocking().retrieve("/hello/Fred") == "Hello Fred"

        counter = self.meterRegistry.get("web.access") \
            .tags("controller", "index", "action", "hello") \
            .counter()
        assert counter.count() == 1.0
