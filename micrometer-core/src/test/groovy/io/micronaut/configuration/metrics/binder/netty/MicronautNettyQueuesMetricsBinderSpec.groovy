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

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ELEMENT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.EXECUTION_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GLOBAL
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GROUP
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.PARENT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.QUEUE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WAIT_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WORKER
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MicronautNettyQueuesMetricsBinderSpec extends Specification {

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

    void "test queue metrics are present"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                [MICRONAUT_METRICS_ENABLED                            : true,
                 (MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled"): true]
        )
        context.getBean(EmbeddedServer).start()

        then:
        eventLoopGroupFactoryInstrumentedClasses
                .collect { context.findBean(it).isPresent() }
                .any()

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
        search.tags(Tags.of(GROUP, PARENT))
        Timer globalParentWaitTimer = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
        search.tags(Tags.of(GROUP, PARENT))
        Timer globalParentExecutionTime = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
        search.tags(Tags.of(GROUP, PARENT))
        Counter globalParentTaskCounter = search.counter()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
        search.tags(Tags.of(GROUP, WORKER))
        Timer globalWorkerWaitTimer = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
        search.tags(Tags.of(GROUP, WORKER))
        Timer globalWorkerExecutionTimer = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
        search.tags(Tags.of(GROUP, WORKER))
        Counter globalWorkerTaskCounter = search.counter()

        then:
        globalParentWaitTimer
        globalParentExecutionTime
        globalParentTaskCounter

        globalWorkerWaitTimer
        globalWorkerWaitTimer.count() == 0
        globalWorkerExecutionTimer
        globalWorkerExecutionTimer.count() == 0
        globalWorkerTaskCounter
        globalWorkerTaskCounter.count() == 0

        when:
        DummyClient client = context.getBean(DummyClient)

        then:
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'
        client.test() == 'root'
        globalParentWaitTimer.count() > 0
        globalParentExecutionTime.count() > 0
        globalWorkerWaitTimer.count() > 0
        globalWorkerExecutionTimer.count() > 0
        globalParentTaskCounter.count() > 0
        globalWorkerTaskCounter.count() > 0

        cleanup:
        context.close()
    }

    @Client('/nettyQueuesMetricsTest')
    private static interface DummyClient {
        @Get
        String test()
    }

    @Controller('/nettyQueuesMetricsTest')
    private static class DummyController {
        @Get
        String root() { "root" }
    }
}
