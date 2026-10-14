package io.micronaut.docs.metrics.annotation;

// tag::imports[]
import io.micrometer.common.annotation.ValueResolver;
import jakarta.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class RegionResolver implements ValueResolver {

    @Override
    public String resolve(Object parameter) {
        return ((Order) parameter).region();
    }
}
// end::class[]
