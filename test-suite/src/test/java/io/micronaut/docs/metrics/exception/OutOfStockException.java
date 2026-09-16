package io.micronaut.docs.metrics.exception;

// tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;
// end::imports[]

// tag::class[]
public class OutOfStockException extends RuntimeException implements HttpResponseProvider {

    @Override
    public HttpResponse<?> getResponse() {
        return HttpResponse.ok(0);
    }
}
// end::class[]
