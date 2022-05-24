package io.micronaut.docs;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseProvider;

public class OutOfStockException extends RuntimeException implements HttpResponseProvider {

    @Override
    public HttpResponse<?> getResponse() {
        return HttpResponse.ok(0);
    }
}
