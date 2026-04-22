package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.search.RequiredSearch
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.netty.channel.EventLoopGroupFactory
import io.micronaut.http.netty.channel.NettyChannelType
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.EventLoopGroup
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ELEMENT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.EXECUTION_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GLOBAL
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GROUP
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.QUEUE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WAIT_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MicronautNettyQueuesMetricsBinderSpec extends Specification {

    private static final String CLIENT_GROUP = 'clients'
    private static final String CLIENT_ID = 'test-client'
    private static List<Class> eventLoopGroupFactoryInstrumentedClasses = [
            InstrumentedNioEventLoopGroupFactory,
            InstrumentedEpollEventLoopGroupFactory,
            InstrumentedKQueueEventLoopGroupFactory
    ]

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        eventLoopGroupFactoryInstrumentedClasses
                .collect { context.findBean(it).isPresent() }
                .any() == result

        cleanup:
        context.close()

        where:
        cfg                                                 | setting | result
        MICRONAUT_METRICS_ENABLED                           | true    | false
        MICRONAUT_METRICS_ENABLED                           | false   | false
        MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled" | true    | true
        MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled" | false   | false
    }

    @Unroll("test getting #channelType channel class from #eventLoopGroupFactory.getClass().getSimpleName()")
    void "test getting channel class from instrumented event loop group factory"(
            NettyChannelType channelType,
            EventLoopGroupFactory eventLoopGroupFactory
    ) {
        when:
        Class channelClass = eventLoopGroupFactory.channelClass(channelType)

        then:
        noExceptionThrown()
        channelClass != null

        where:
        [channelType, eventLoopGroupFactory] << [
                NettyChannelType.values(),
                [
                        new InstrumentedNioEventLoopGroupFactory(null),
                        new InstrumentedEpollEventLoopGroupFactory(null),
                        new InstrumentedKQueueEventLoopGroupFactory(null)
                ]
        ].combinations()
    }

    @Unroll("test if #eventLoopGroupFactory.getClass().getSimpleName() is native")
    void "test if instrumented event loop group factory is native"() {
        when:
        boolean isNative = eventLoopGroupFactory.isNative()

        then:
        isNative == result

        where:
        eventLoopGroupFactory                             | result
        new InstrumentedNioEventLoopGroupFactory(null)    | false
        new InstrumentedEpollEventLoopGroupFactory(null)  | true
        new InstrumentedKQueueEventLoopGroupFactory(null) | true
    }

    void "test queue metrics are present for configured client event loop group"() {
        when:
        int port = nextAvailablePort()
        Map<String, Object> config = [
                (MICRONAUT_METRICS_ENABLED)                            : true,
                (MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled") : true,
                (MICRONAUT_METRICS_BINDERS + ".executor.enabled")     : false,
                'micronaut.server.port'                               : port,
                'spec.name'                                           : getClass().getSimpleName()
        ]
        config["micronaut.http.services.${CLIENT_ID}.url".toString()] = "http://localhost:${port}".toString()
        config["micronaut.http.services.${CLIENT_ID}.event-loop-group".toString()] = CLIENT_GROUP
        config["micronaut.netty.event-loops.${CLIENT_GROUP}.num-threads".toString()] = 1
        ApplicationContext context = ApplicationContext.run(config)
        context.getBean(EmbeddedServer).start()

        then:
        eventLoopGroupFactoryInstrumentedClasses
                .collect { context.findBean(it).isPresent() }
                .any()

        when:
        DummyClient client = context.getBean(DummyClient)
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'

        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
        search.tags(Tags.of(GROUP, CLIENT_GROUP))
        Timer globalClientWaitTimer = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
        search.tags(Tags.of(GROUP, CLIENT_GROUP))
        Timer globalClientExecutionTime = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
        search.tags(Tags.of(GROUP, CLIENT_GROUP))
        Counter globalClientTaskCounter = search.counter()

        then:
        globalClientWaitTimer.count() > 0
        globalClientExecutionTime.count() > 0
        globalClientTaskCounter.count() > 0

        cleanup:
        context.close()
    }

    void "test executor metrics are present for configured client event loop group"() {
        given:
        int port = nextAvailablePort()
        Map<String, Object> config = [
                (MICRONAUT_METRICS_ENABLED)                        : true,
                'micronaut.server.port'                           : port,
                'spec.name'                                       : getClass().getSimpleName()
        ]
        config["micronaut.http.services.${CLIENT_ID}.url".toString()] = "http://localhost:${port}".toString()
        config["micronaut.http.services.${CLIENT_ID}.event-loop-group".toString()] = CLIENT_GROUP
        config["micronaut.netty.event-loops.${CLIENT_GROUP}.num-threads".toString()] = 1
        ApplicationContext context = ApplicationContext.run(config)
        context.getBean(EmbeddedServer).start()
        EventLoopGroup eventLoopGroup = context.getBean(EventLoopGroup, Qualifiers.byName(CLIENT_GROUP))
        MeterRegistry registry = context.getBean(MeterRegistry)
        CountDownLatch firstTaskStarted = new CountDownLatch(1)
        CountDownLatch releaseFirstTask = new CountDownLatch(1)

        when:
        eventLoopGroup.execute {
            firstTaskStarted.countDown()
            releaseFirstTask.await(10, TimeUnit.SECONDS)
        }
        assert firstTaskStarted.await(10, TimeUnit.SECONDS)
        eventLoopGroup.execute { }

        Gauge queuedTasks = registry.get("executor.queued")
                .tag("name", CLIENT_GROUP)
                .gauge()
        Gauge poolSize = registry.get("executor.pool.size")
                .tag("name", CLIENT_GROUP)
                .gauge()

        then:
        new PollingConditions(timeout: 3, delay: 0.1).eventually {
            queuedTasks.value() > 0
            poolSize.value() > 0
        }

        cleanup:
        releaseFirstTask.countDown()
        context.close()
    }

    @io.micronaut.context.annotation.Requires(property = "spec.name", value = "MicronautNettyQueuesMetricsBinderSpec")
    @Client(id = CLIENT_ID, path = '/nettyQueuesMetricsTest')
    private static interface DummyClient {
        @Get
        String test()
    }

    @io.micronaut.context.annotation.Requires(property = "spec.name", value = "MicronautNettyQueuesMetricsBinderSpec")
    @Controller('/nettyQueuesMetricsTest')
    private static class DummyController {
        @Get
        String root() { "root" }
    }

    private static int nextAvailablePort() {
        ServerSocket socket = new ServerSocket(0)
        try {
            return socket.localPort
        } finally {
            socket.close()
        }
    }
}
