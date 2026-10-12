package io.micronaut.docs.metrics.tags

// tag::imports[]
import io.micrometer.core.instrument.Tag
import io.micronaut.configuration.metrics.binder.web.HttpMetricsTagProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import jakarta.inject.Singleton
// end::imports[]

import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "TenantTagProviderSpec")
// tag::class[]
@Singleton
class TenantTagProvider implements HttpMetricsTagProvider {

    @Override
    Iterable<Tag> getTags(HttpRequest<?> request, HttpResponse<?> response, Throwable throwable) {
        String tenant = request.headers.get("X-Tenant")
        [Tag.of("tenant", tenant ?: "none")]
    }
}
// end::class[]
