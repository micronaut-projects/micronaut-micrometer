package io.micronaut.docs.metrics.annotation

// tag::imports[]
import io.micrometer.core.annotation.Timed
import io.micrometer.core.aop.MeterTag
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
open class OrderService {

    @Timed("orders.lookup")
    @MeterTag("status") // <1>
    @MeterTag(key = "label", expression = "#{ 'tenant-' + #tenant }") // <2>
    open fun lookup(@MeterTag("tenant") tenant: String, // <3>
                    @MeterTag(key = "region", resolver = RegionResolver::class) order: Order): String { // <4>
        return "shipped"
    }
}
// end::class[]
