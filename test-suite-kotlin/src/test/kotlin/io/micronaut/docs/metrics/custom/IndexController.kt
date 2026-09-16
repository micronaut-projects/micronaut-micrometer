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
open class IndexController(private val meterRegistry: MeterRegistry) {

    @Get("/hello/{name}")
    open fun hello(@NotBlank name: String): Mono<String> {
        meterRegistry
            .counter("web.access", "controller", "index", "action", "hello")
            .increment()
        return Mono.just("Hello $name")
    }
}
// end::class[]
