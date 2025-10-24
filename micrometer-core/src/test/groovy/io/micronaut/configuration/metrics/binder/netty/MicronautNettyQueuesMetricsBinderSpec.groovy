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

class MicronautNettyQueuesMetricsBinderSpec extends Specification {

    void "test queue metrics are present"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                [MICRONAUT_METRICS_ENABLED                            : true,
                 (MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled"): true]
        )
        context.getBean(EmbeddedServer).start()

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
        search.tags(Tags.of(GROUP, "default"))
        Timer globalParentWaitTimer = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
        search.tags(Tags.of(GROUP, "default"))
        Timer globalParentExecutionTime = search.timer()

        search = registry.get(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
        search.tags(Tags.of(GROUP, "default"))
        Counter globalParentTaskCounter = search.counter()

        then:
        globalParentWaitTimer
        globalParentExecutionTime
        globalParentTaskCounter

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
        globalParentTaskCounter.count() > 0

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
