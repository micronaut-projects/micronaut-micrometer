package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.search.RequiredSearch
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanProvider
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.AdaptiveByteBufAllocator
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import static io.micronaut.configuration.metrics.binder.netty.ByteBufAllocatorMetricsConfig.ByteBufAllocatorMetricKind.POOLED_ALLOCATOR
import static io.micronaut.configuration.metrics.binder.netty.ByteBufAllocatorMetricsConfig.ByteBufAllocatorMetricKind.UNPOOLED_ALLOCATOR
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ACTIVE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ADAPTIVE
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
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.UNPOOLED
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

        when: "Netty's default allocator is the adaptive allocator"
        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get(dot(NETTY, ALLOC, MEMORY, USED))
        search.tags(Tags.of(ALLOC, ADAPTIVE).and(MEMORY, DIRECT))
        Gauge gauge = search.gauge()

        then:
        ByteBufAllocator.DEFAULT instanceof AdaptiveByteBufAllocator
        gauge
        gauge.value() >= 0
        registry.find(dot(NETTY, ALLOC, MEMORY, USED)).tag(ALLOC, UNPOOLED).gauges().size() == 2
        registry.find(dot(NETTY, ALLOC, MEMORY, USED)).tag(ALLOC, POOLED).gauges().isEmpty()
        registry.find(dot(NETTY, ALLOC, ARENA, COUNT)).gauges().isEmpty()

        when:
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
            gauge.value() > 0
        }

        cleanup:
        context.close()
    }

    void "test pooled allocator metrics when the pooled allocator is the default allocator"() {
        given:
        MeterRegistry registry = new SimpleMeterRegistry()
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true)

        when:
        binder(registry).configureNettyMetrics(allocator)
        Gauge threadLocalCaches = registry.get('netty.alloc.thread.local.cache.count').tags(ALLOC, POOLED).gauge()

        then:
        allocator.metric().numDirectArenas() > 0
        allocator.metric().numThreadLocalCaches() == 0
        threadLocalCaches.value() == 0
        threadLocalCaches.id.description == 'The number of thread local caches.'
        registry.find(dot(NETTY, ALLOC, MEMORY, USED)).tag(ALLOC, POOLED).gauges().size() == 2
        registry.get(dot(NETTY, ALLOC, ARENA, COUNT)).tags(ALLOC, POOLED, MEMORY, DIRECT).gauge().value() == allocator.metric().numDirectArenas()
        registry.find(dot(NETTY, ALLOC, MEMORY, USED)).tag(ALLOC, ADAPTIVE).gauges().isEmpty()

        when: 'a thread allocates from the pooled allocator'
        def thread = new Thread({ allocator.directBuffer(16).release() })
        thread.start()
        thread.join()

        then:
        threadLocalCaches.value() == allocator.metric().numThreadLocalCaches()

        cleanup:
        registry.close()
    }

    void "test pooled allocator is not metered when it is not the default allocator"() {
        given:
        MeterRegistry registry = new SimpleMeterRegistry()

        when:
        binder(registry).configureNettyMetrics(allocator)

        then:
        registry.find(dot(NETTY, ALLOC, MEMORY, USED)).tag(ALLOC, POOLED).gauges().isEmpty()
        registry.find(dot(NETTY, ALLOC, MEMORY, USED)).tag(ALLOC, UNPOOLED).gauges().size() == 2
        registry.find(dot(NETTY, ALLOC, MEMORY, USED)).tag(ALLOC, ADAPTIVE).gauges().size() == adaptiveGauges

        cleanup:
        registry.close()

        where:
        allocator                         | adaptiveGauges
        new AdaptiveByteBufAllocator()    | 2
        UnpooledByteBufAllocator.DEFAULT  | 0
    }

    @Unroll
    void "test ByteBufAllocator metrics pooled arena counter meters share consistent tag keys for #name"() {
        when:
        MeterRegistry registry = new SimpleMeterRegistry()
        binder(registry).configureNettyMetrics(new PooledByteBufAllocator(true))
        Collection<Meter> meters = registry.find(name).meters()
        Set<Set<String>> distinctTagKeySets = meters.collect { Meter m -> m.id.tags.collect { it.key } as Set } as Set
        Set<String> sizeValues = meters.collect { Meter m -> m.id.getTag(SIZE) } as Set

        then:
        !meters.isEmpty()
        distinctTagKeySets.size() == 1
        sizeValues == ['total', 'small', 'normal', 'huge'] as Set

        cleanup:
        registry.close()

        where:
        name << [dot(NETTY, ALLOC, ARENA, ALLOCATION, COUNT),
                 dot(NETTY, ALLOC, ARENA, DEALLOCATION, COUNT),
                 dot(NETTY, ALLOC, ARENA, ALLOCATION, ACTIVE, COUNT)]
    }

    private static ByteBufAllocatorMetricsBinder binder(MeterRegistry registry) {
        new ByteBufAllocatorMetricsBinder({ registry } as BeanProvider<MeterRegistry>, new ByteBufAllocatorMetricsConfig())
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
