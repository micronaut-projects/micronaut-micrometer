package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
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

import static io.micronaut.configuration.metrics.binder.netty.ByteBufAllocatorMetricsConfig.ByteBufAllocatorMetricKind.POOLED_ALLOCATOR
import static io.micronaut.configuration.metrics.binder.netty.ByteBufAllocatorMetricsConfig.ByteBufAllocatorMetricKind.UNPOOLED_ALLOCATOR
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ACTIVE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ALLOC
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ALLOCATION
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ARENA
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.DEALLOCATION
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.DIRECT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.MEMORY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.POOLED
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.SIZE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.USED
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MicronautNettyByteBufAllocatorMetricsBinderSpec extends Specification {

    void "test ByteBufAllocator config defaults and metric normalization"() {
        given:
        ByteBufAllocatorMetricsConfig config = new ByteBufAllocatorMetricsConfig()

        expect:
        !config.enabled
        config.metrics == EnumSet.allOf(ByteBufAllocatorMetricsConfig.ByteBufAllocatorMetricKind)

        when:
        config.enabled = true
        config.metrics = EnumSet.of(POOLED_ALLOCATOR)

        then:
        config.enabled
        config.metrics == EnumSet.of(POOLED_ALLOCATOR)

        when:
        config.metrics = null

        then:
        config.metrics == EnumSet.allOf(ByteBufAllocatorMetricsConfig.ByteBufAllocatorMetricKind)

        when:
        config.metrics = [] as Set

        then:
        config.metrics == EnumSet.allOf(ByteBufAllocatorMetricsConfig.ByteBufAllocatorMetricKind)
    }

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
        ByteBufAllocatorMetricsConfig config = context.getBean(ByteBufAllocatorMetricsConfig)

        then:
        optBinder.isPresent()
        config.enabled
        config.metrics.size() == 2
        config.metrics.contains(POOLED_ALLOCATOR)
        config.metrics.contains(UNPOOLED_ALLOCATOR)
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

    @Unroll
    void "test ByteBufAllocator metrics pooled arena counter meters share consistent tag keys for #name"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                [MICRONAUT_METRICS_ENABLED                                        : true,
                 (MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled"): true]
        )
        MeterRegistry registry = context.getBean(MeterRegistry)
        Collection<Meter> meters = registry.find(name).meters()
        Set<Set<String>> distinctTagKeySets = meters.collect { Meter m -> m.id.tags.collect { it.key } as Set } as Set
        Set<String> sizeValues = meters.collect { Meter m -> m.id.getTag(SIZE) } as Set

        then:
        !meters.isEmpty()
        distinctTagKeySets.size() == 1
        sizeValues == ['total', 'small', 'normal', 'huge'] as Set

        cleanup:
        context.close()

        where:
        name << [dot(NETTY, ALLOC, ARENA, ALLOCATION, COUNT),
                 dot(NETTY, ALLOC, ARENA, DEALLOCATION, COUNT),
                 dot(NETTY, ALLOC, ARENA, ALLOCATION, ACTIVE, COUNT)]
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
