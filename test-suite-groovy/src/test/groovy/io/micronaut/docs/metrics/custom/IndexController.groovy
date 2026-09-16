package io.micronaut.docs.metrics.custom

// tag::imports[]
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import jakarta.validation.constraints.NotBlank
import reactor.core.publisher.Mono
// end::imports[]

// tag::class[]
@Controller
class IndexController {

    private final MeterRegistry meterRegistry

    IndexController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry
    }

    @Get("/hello/{name}")
    Mono<String> hello(@NotBlank String name) {
        meterRegistry
                .counter("web.access", "controller", "index", "action", "hello")
                .increment()
        Mono.just("Hello " + name)
    }
}
// end::class[]
