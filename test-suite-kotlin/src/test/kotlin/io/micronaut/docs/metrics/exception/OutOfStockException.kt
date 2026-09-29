package io.micronaut.docs.metrics.exception

// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseProvider
// end::imports[]

// tag::class[]
class OutOfStockException : RuntimeException(), HttpResponseProvider {

    override fun getResponse(): HttpResponse<*> = HttpResponse.ok(0)
}
// end::class[]
