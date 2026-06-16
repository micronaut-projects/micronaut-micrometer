package io.micronaut.configuration.metrics.binder.netty

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.BeanProvider
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ELEMENT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.EXECUTION_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GLOBAL
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GROUP
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NUMBER
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.PARENT
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.QUEUE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.SIZE
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WAIT_TIME
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WORKER
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot

class NettyQueueMetricsSupportSpec extends Specification {

    MeterRegistry registry = new SimpleMeterRegistry()

    void "test parent and worker queues share global meters and record task metrics"() {
        given:
        NettyQueueMetricsSupport support = new NettyQueueMetricsSupport(meterRegistryProvider())
        Queue<Runnable> parentQueue = support.wrapTaskQueue(PARENT, new ArrayDeque<Runnable>())
        Queue<Runnable> workerQueue = support.wrapTaskQueue(WORKER, new ArrayDeque<Runnable>())
        AtomicInteger executions = new AtomicInteger()

        when:
        parentQueue.offer { executions.incrementAndGet() }
        workerQueue.add { executions.addAndGet(10) }

        then:
        parentQueue.size() == 1
        workerQueue.size() == 1
        taskCounter(PARENT).count() == 1
        taskCounter(WORKER).count() == 1
        queueSize(PARENT, "0").value() == 1
        queueSize(WORKER, "0").value() == 1

        when:
        parentQueue.poll().run()
        workerQueue.remove().run()

        then:
        executions.get() == 11
        globalTimer(PARENT, WAIT_TIME).count() == 1
        globalTimer(PARENT, EXECUTION_TIME).count() == 1
        globalTimer(WORKER, WAIT_TIME).count() == 1
        globalTimer(WORKER, EXECUTION_TIME).count() == 1
    }

    void "test new task queue supports bounded and unbounded capacities"() {
        given:
        NettyQueueMetricsSupport support = new NettyQueueMetricsSupport(meterRegistryProvider())

        when:
        Queue<Runnable> unbounded = support.newTaskQueue(PARENT, Integer.MAX_VALUE)
        Queue<Runnable> bounded = support.newTaskQueue(WORKER, 8)
        unbounded.offer { }
        bounded.offer { }

        then:
        unbounded.size() == 1
        bounded.size() == 1
        queueSize(PARENT, "0").value() == 1
        queueSize(WORKER, "0").value() == 1
        taskCounter(PARENT).count() == 1
        taskCounter(WORKER).count() == 1
    }

    void "test server task queue interceptor normalizes server groups and preserves other groups"() {
        given:
        Queue<Runnable> parentQueue = new ArrayDeque<>()
        Queue<Runnable> workerQueue = new ArrayDeque<>()
        Queue<Runnable> otherQueue = new ArrayDeque<>()
        BeanProvider<NettyHttpServerConfiguration> serverConfigurationProvider = Stub() {
            find(_) >> Optional.empty()
        }
        NettyQueueMetricsSupport support = new NettyQueueMetricsSupport(meterRegistryProvider())
        InstrumentedTaskQueueInterceptor interceptor = new InstrumentedTaskQueueInterceptor(serverConfigurationProvider, support)

        when:
        Queue<Runnable> parentResult = interceptor.wrapTaskQueue(PARENT, parentQueue)
        Queue<Runnable> workerResult = interceptor.wrapTaskQueue(EventLoopGroupConfiguration.DEFAULT, workerQueue)
        Queue<Runnable> otherResult = interceptor.wrapTaskQueue("other", otherQueue)
        parentResult.offer { }
        workerResult.offer { }
        otherResult.offer { }

        then:
        !parentResult.is(parentQueue)
        !workerResult.is(workerQueue)
        !otherResult.is(otherQueue)
        taskCounter(PARENT).count() == 1
        taskCounter(WORKER).count() == 1
        taskCounter("other").count() == 1
        queueSize(PARENT, "0").value() == 1
        queueSize(WORKER, "0").value() == 1
        queueSize("other", "0").value() == 1
    }

    private BeanProvider<MeterRegistry> meterRegistryProvider() {
        Stub(BeanProvider) {
            get() >> registry
        }
    }

    private Counter taskCounter(String group) {
        registry.get(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
                .tag(GROUP, group)
                .counter()
    }

    private Gauge queueSize(String group, String number) {
        registry.get(dot(NETTY, QUEUE, SIZE))
                .tag(GROUP, group)
                .tag(QUEUE, SIZE)
                .tag(NUMBER, number)
                .gauge()
    }

    private Timer globalTimer(String group, String timerName) {
        registry.get(dot(NETTY, QUEUE, GLOBAL, timerName))
                .tag(GROUP, group)
                .timer()
    }
}
