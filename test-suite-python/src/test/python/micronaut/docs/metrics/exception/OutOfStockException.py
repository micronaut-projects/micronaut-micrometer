# tag::imports[]
from java.lang import RuntimeException
from micronaut.http import HttpResponse, HttpResponseProvider
# end::imports[]


# tag::class[]
class OutOfStockException(RuntimeException, HttpResponseProvider):

    def getResponse(self) -> HttpResponse:
        return HttpResponse.ok(0)
# end::class[]
