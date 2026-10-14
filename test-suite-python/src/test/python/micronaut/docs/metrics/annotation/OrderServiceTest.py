from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Order import Order
from .OrderService import OrderService


@MicronautTest
class OrderServiceTest:
    meterRegistry: Annotated[MeterRegistry, Inject]
    orderService: Annotated[OrderService, Inject]

    @Test
    def test_the_meter_tags_are_added_to_the_timer(self):
        assert self.orderService.lookup("acme", Order("eu")) == "shipped"

        timer = self.meterRegistry.get("orders.lookup") \
            .tags("status", "shipped", "label", "tenant-acme", "tenant", "acme", "region", "eu") \
            .timer()
        assert timer.count() == 1
