from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.server.exceptions import ExceptionHandler

from .OutOfStockException import OutOfStockException


@Requires(property="spec.name", value="OutOfStockExceptionTest")
@Singleton
class OutOfStockExceptionHandler(ExceptionHandler[OutOfStockException, HttpResponse]):

    def handle(self, request: HttpRequest, exception: OutOfStockException) -> HttpResponse:
        return exception.getResponse()
