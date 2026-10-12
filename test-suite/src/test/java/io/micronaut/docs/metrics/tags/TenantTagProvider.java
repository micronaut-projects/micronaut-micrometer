package io.micronaut.docs.metrics.tags;

// tag::imports[]
import io.micrometer.core.instrument.Tag;
import io.micronaut.configuration.metrics.binder.web.HttpMetricsTagProvider;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Singleton;

import java.util.List;
// end::imports[]

import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "TenantTagProviderTest")
// tag::class[]
@Singleton
public class TenantTagProvider implements HttpMetricsTagProvider {

    @Override
    public Iterable<Tag> getTags(HttpRequest<?> request, HttpResponse<?> response, Throwable throwable) {
        String tenant = request.getHeaders().get("X-Tenant");
        return List.of(Tag.of("tenant", tenant == null ? "none" : tenant));
    }
}
// end::class[]
