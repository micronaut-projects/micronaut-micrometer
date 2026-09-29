package io.micronaut.docs.metrics.exception

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "OutOfStockExceptionTest")
@Singleton
class OutOfStockExceptionHandler : ExceptionHandler<OutOfStockException, HttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: OutOfStockException): HttpResponse<*> =
        exception.response
}
