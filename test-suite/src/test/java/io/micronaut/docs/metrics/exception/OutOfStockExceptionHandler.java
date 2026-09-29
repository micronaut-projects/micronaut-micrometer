package io.micronaut.docs.metrics.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "OutOfStockExceptionTest")
@Singleton
class OutOfStockExceptionHandler implements ExceptionHandler<OutOfStockException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, OutOfStockException exception) {
        return exception.getResponse();
    }
}
