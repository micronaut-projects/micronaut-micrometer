package io.micronaut.docs.metrics.exception

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = "spec.name", value = "OutOfStockExceptionSpec")
@Controller("/stock")
class StockController {

    @Get("/{name}")
    int stock(String name) {
        throw new OutOfStockException()
    }
}
