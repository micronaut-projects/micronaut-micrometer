package io.micronaut.docs.metrics.annotation

// tag::imports[]
import io.micrometer.common.annotation.ValueResolver
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class RegionResolver implements ValueResolver {

    @Override
    String resolve(Object parameter) {
        ((Order) parameter).region()
    }
}
// end::class[]
