from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Get

from .OutOfStockException import OutOfStockException


@Requires(property="spec.name", value="OutOfStockExceptionTest")
@Controller("/stock")
class StockController:

    @Get("/{name}")
    def stock(self, name: str) -> int:
        raise OutOfStockException()
