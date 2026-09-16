from typing import Annotated

# tag::imports[]
from jakarta.validation.constraints import NotBlank
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Controller, Get
from micronaut.validation import Validated
from org.reactivestreams import Publisher
from reactor.core.publisher import Mono

try:
    from io.micrometer.core.instrument import MeterRegistry
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from micrometer.core.instrument import MeterRegistry
# end::imports[]


# tag::class[]
@Validated
@Controller
class IndexController:

    def __init__(self, meterRegistry: MeterRegistry):
        self.meterRegistry = meterRegistry

    @Get("/hello/{name}")
    @SingleResult
    def hello(self, name: Annotated[str, NotBlank]) -> Publisher[str]:
        self.meterRegistry \
            .counter("web.access", "controller", "index", "action", "hello") \
            .increment()
        return Mono.just("Hello " + name)
# end::class[]
