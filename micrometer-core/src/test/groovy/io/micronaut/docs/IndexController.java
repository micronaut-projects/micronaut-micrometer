package io.micronaut.docs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import reactor.core.publisher.Mono;

import javax.validation.constraints.NotBlank;

@Controller
class IndexController {

    private final MeterRegistry meterRegistry;

    IndexController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Get("/hello/{name}")
    Mono<String> hello(@NotBlank String name) {
        meterRegistry
                .counter("web.access", "controller", "index", "action", "hello")
                .increment();
        return Mono.just("Hello " + name);
    }
}
