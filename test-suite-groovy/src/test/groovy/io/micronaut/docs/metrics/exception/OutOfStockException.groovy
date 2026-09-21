package io.micronaut.docs.metrics.exception

// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseProvider
// end::imports[]

// tag::class[]
class OutOfStockException extends RuntimeException implements HttpResponseProvider {

    @Override
    HttpResponse<?> getResponse() {
        HttpResponse.ok(0)
    }
}
// end::class[]
