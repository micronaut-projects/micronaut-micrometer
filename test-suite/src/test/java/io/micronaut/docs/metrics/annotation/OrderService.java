package io.micronaut.docs.metrics.annotation;

// tag::imports[]
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import jakarta.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class OrderService {

    @Timed("orders.lookup")
    @MeterTag("status") // <1>
    @MeterTag(key = "label", expression = "#{ 'tenant-' + #tenant }") // <2>
    public String lookup(@MeterTag("tenant") String tenant, // <3>
                         @MeterTag(key = "region", resolver = RegionResolver.class) Order order) { // <4>
        return "shipped";
    }
}
// end::class[]
