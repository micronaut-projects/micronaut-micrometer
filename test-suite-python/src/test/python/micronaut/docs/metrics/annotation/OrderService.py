from typing import Annotated

# tag::imports[]
from io.micrometer.core.annotation import Timed
from io.micrometer.core.aop import MeterTag
from jakarta.inject import Singleton

from .Order import Order
from .RegionResolver import RegionResolver
# end::imports[]


# tag::class[]
@Singleton
class OrderService:

    @Timed("orders.lookup")
    @MeterTag("status")  # <1>
    @MeterTag(key="label", expression="#{ 'tenant-' + #tenant }")  # <2>
    def lookup(self,
               tenant: Annotated[str, MeterTag("tenant")],  # <3>
               order: Annotated[Order, MeterTag(key="region", resolver=RegionResolver)]) -> str:  # <4>
        return "shipped"
# end::class[]
