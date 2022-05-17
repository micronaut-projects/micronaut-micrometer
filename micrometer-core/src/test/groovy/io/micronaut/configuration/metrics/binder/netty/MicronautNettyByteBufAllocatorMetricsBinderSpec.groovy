package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.search.RequiredSearch
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import static io.micronaut.configuration.metrics.binder.netty.ByteBufAllocatorMetricsBinder.ByteBufAllocatorMetricKind.POOLED_ALLOCATOR
import static io.micronaut.configuration.metrics.binder.netty.ByteBufAllocatorMetricsBinder.ByteBufAllocatorMetricKind.UNPOOLED_ALLOCATOR
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ALLOC
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.DIRECT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.MEMORY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.POOLED
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.USED
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MicronautNettyByteBufAllocatorMetricsBinderSpec extends Specification {

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(ByteBufAllocatorMetricsBinder).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                                                             | setting | result
        MICRONAUT_METRICS_ENABLED                                       | true    | false
        MICRONAUT_METRICS_ENABLED                                       | false   | false
        MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled" | true    | true
        MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled" | false   | false
    }

    void "test ByteBufAllocator custom metrics"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                [MICRONAUT_METRICS_ENABLED                                        : true,
                 (MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled"): true,
                 (MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.metrics"): [POOLED_ALLOCATOR, UNPOOLED_ALLOCATOR]]
        )
        Optional<ByteBufAllocatorMetricsBinder> optBinder = context.findBean(ByteBufAllocatorMetricsBinder)

        then:
        optBinder.isPresent()
        optBinder.get().kinds.size() == 2
        optBinder.get().kinds.contains(POOLED_ALLOCATOR)
        optBinder.get().kinds.contains(UNPOOLED_ALLOCATOR)

        cleanup:
        context.close()
    }

    void "test ByteBufAllocator metrics binder is present"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                [MICRONAUT_METRICS_ENABLED                                        : true,
                 (MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled"): true]
        )

        then:
        context.containsBean(ByteBufAllocatorMetricsBinder)

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)
        Tags pooled = Tags.of(ALLOC, POOLED)
        RequiredSearch search = registry.get(dot(NETTY, ALLOC, MEMORY, USED))
        search.tags(pooled.and(MEMORY, DIRECT))
        Gauge gauge = search.gauge()

        then:
        gauge
        gauge.value() >= 0

        when:
        def initialValue = gauge.value()
        def server = context.getBean(EmbeddedServer)
        server.start()
        ByteBufAllocatorMetricTestDummyClient client = context.getBean(ByteBufAllocatorMetricTestDummyClient)

        then:
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        new PollingConditions(timeout: 3, delay: 0.1).eventually {
            gauge.value() >= initialValue
        }

        cleanup:
        context.close()
    }

    @Client('/bytebufallocatortest')
    private static interface ByteBufAllocatorMetricTestDummyClient {
        @Get
        String root()
    }

    @Controller('/bytebufallocatortest')
    private static class ByteBufAllocatorMetricTestController {
        @Get
        String root() { "root" }
    }
}
