package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.search.RequiredSearch
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ACTIVE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.BYTE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.CHANNEL
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.READ
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MicronautNettyChannelMetricsBinderSpec extends Specification {

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(NettyMetricsPipelineBinder).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                                                   | setting | result
        MICRONAUT_METRICS_ENABLED                             | true    | false
        MICRONAUT_METRICS_ENABLED                             | false   | false
        MICRONAUT_METRICS_BINDERS + ".netty.channels.enabled" | true    | true
        MICRONAUT_METRICS_BINDERS + ".netty.channels.enabled" | false   | false
    }

    void "test channel metrics meters are present"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                [MICRONAUT_METRICS_ENABLED                              : true,
                 (MICRONAUT_METRICS_BINDERS + ".netty.channels.enabled"): true]
        )
        context.getBean(EmbeddedServer).start()

        then:
        context.containsBean(NettyMetricsPipelineBinder)

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get(dot(NETTY, CHANNEL, COUNT))
        search.tags(Tags.of(CHANNEL, COUNT))
        Counter channelCounter = search.counter()

        search = registry.get(dot(NETTY, CHANNEL, BYTE))
        search.tags(Tags.of(BYTE, READ))
        Counter bytesRead = search.counter()

        search = registry.get(dot(NETTY, CHANNEL, TIME))
        search.tags(Tags.of(ACTIVE, TIME))
        Timer activeChannelTimer = search.timer()

        then:
        channelCounter
        channelCounter.count() == 0
        bytesRead
        bytesRead.count() == 0
        activeChannelTimer
        activeChannelTimer.count() == 0

        when:
        DummyClient client = context.getBean(DummyClient)

        then:
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        channelCounter.count() > 0
        bytesRead.count() > 0
        activeChannelTimer.count() > 0

        cleanup:
        context.close()
    }

    @Client('/dummy')
    private static interface DummyClient {
        @Get
        String root()
    }

    @Controller('/dummy')
    private static class DummyController {
        @Get
        String root() { "root" }
    }
}
