package io.micronaut.docs;

// tag::imports[]
import io.micrometer.core.instrument.Tag;
import io.micronaut.configuration.metrics.binder.web.HttpMetricsTagProvider;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
// end::imports[]

import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "HttpMetricsTagProviderExample")
// tag::class[]
@Singleton
public class HttpMetricsTagProviderExample implements HttpMetricsTagProvider {
    @Override
    public Iterable<Tag> getTags(HttpRequest<?> request, HttpResponse<?> response, Throwable throwable) {
        List<String> tenants = request.getHeaders().getAll("X-Tenant");
        String tenant = tenants.isEmpty() ? "none" : tenants.get(0);
        return Collections.singletonList(Tag.of("tenant", tenant));
    }
}
// end::class[]
