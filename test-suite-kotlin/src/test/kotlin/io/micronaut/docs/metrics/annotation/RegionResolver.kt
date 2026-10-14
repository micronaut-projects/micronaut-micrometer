package io.micronaut.docs.metrics.annotation

// tag::imports[]
import io.micrometer.common.annotation.ValueResolver
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class RegionResolver : ValueResolver {

    override fun resolve(parameter: Any?): String = (parameter as Order).region
}
// end::class[]
