package io.micronaut.docs.metrics.tags

// tag::imports[]
import io.micrometer.core.instrument.Tag
import io.micronaut.configuration.metrics.binder.web.HttpMetricsTagProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "TenantTagProviderTest")
// tag::class[]
@Singleton
class TenantTagProvider : HttpMetricsTagProvider {

    override fun getTags(request: HttpRequest<*>, response: HttpResponse<*>?, throwable: Throwable?): Iterable<Tag> {
        val tenant = request.headers["X-Tenant"]
        return listOf(Tag.of("tenant", tenant ?: "none"))
    }
}
// end::class[]
