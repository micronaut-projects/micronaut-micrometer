from typing import Annotated

# tag::imports[]
from io.micrometer.core.instrument import MeterRegistry
from jakarta.validation.constraints import NotBlank
from micronaut.http.annotation import Controller, Get
from reactor.core.publisher import Mono
# end::imports[]


# tag::class[]
@Controller
class IndexController:

    def __init__(self, meterRegistry: MeterRegistry):
        self.meterRegistry = meterRegistry

    @Get("/hello/{name}")
    def hello(self, name: Annotated[str, NotBlank]) -> Mono[str]:
        self.meterRegistry \
            .counter("web.access", "controller", "index", "action", "hello") \
            .increment()
        return Mono.just("Hello " + name)
# end::class[]
