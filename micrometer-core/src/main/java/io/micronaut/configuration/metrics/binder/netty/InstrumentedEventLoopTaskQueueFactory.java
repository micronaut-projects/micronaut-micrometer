/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.binder.netty;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.TaskQueueInterceptor;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.util.internal.PlatformDependent;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ELEMENT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.EXECUTION_TIME;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GLOBAL;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.GROUP;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.PARENT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.QUEUE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WAIT_TIME;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WORKER;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instrumented Event Loop Queue factory.
 *
 * @author Christophe Roudet
 * @since 2.0
 */
@Singleton
@Named("InstrumentedEventLoopTaskQueueFactory")
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = FALSE, notEquals = FALSE)
@Requires(classes = EventLoopTaskQueueFactory.class)
@Internal
@SuppressWarnings("java:S1874")
final class InstrumentedEventLoopTaskQueueFactory implements EventLoopTaskQueueFactory, TaskQueueInterceptor {

    private final BeanProvider<MeterRegistry> meterRegistryProvider;
    private final BeanContext beanContext;
    private final ConcurrentMap<String, EventLoopGroupMetrics> metrics = new ConcurrentHashMap<>();

    /**
     * @param meterRegistryProvider the metric registry provider
     * @param beanContext the bean context
     */
    public InstrumentedEventLoopTaskQueueFactory(BeanProvider<MeterRegistry> meterRegistryProvider,
                                                 BeanContext beanContext) {
        this.meterRegistryProvider = meterRegistryProvider;
        this.beanContext = beanContext;
        metrics.put(PARENT, EventLoopGroupMetrics.create(meterRegistryProvider.get(), PARENT));
        metrics.put(WORKER, EventLoopGroupMetrics.create(meterRegistryProvider.get(), WORKER));
    }

    @Override
    public Queue<Runnable> newTaskQueue(int maxCapacity) {
        final String kind = findOrigin();
        return newMonitoredQueue(kind,
                maxCapacity == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue() : PlatformDependent.<Runnable>newMpscQueue(maxCapacity));
    }

    @Override
    public Queue<Runnable> wrapTaskQueue(String groupName, Queue<Runnable> original) {
        return newMonitoredQueue(groupName, original);
    }

    private Queue<Runnable> newMonitoredQueue(String groupName, Queue<Runnable> queue) {
        String name = normalizeGroupName(groupName);
        EventLoopGroupMetrics groupMetrics = metrics.computeIfAbsent(name, group -> EventLoopGroupMetrics.create(meterRegistryProvider.get(), group));
        return new MonitoredQueue(groupMetrics.queueCounter.incrementAndGet(),
                meterRegistryProvider.get(),
                Tag.of(GROUP, name),
                groupMetrics.taskCounter,
                groupMetrics.globalWaitTimeTimer,
                groupMetrics.globalExecutionTimer,
                queue);
    }

    private String normalizeGroupName(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return WORKER;
        }
        if (PARENT.equals(groupName) || WORKER.equals(groupName) || EventLoopGroupConfiguration.DEFAULT.equals(groupName)) {
            return groupName;
        }
        return beanContext.findBean(EventLoopGroupConfiguration.class, Qualifiers.byName(groupName)).isPresent() ? groupName : WORKER;
    }

    private String findOrigin() {
        for (StackTraceElement elt: Thread.currentThread().getStackTrace()) {
            if (NettyHttpServer.class.getName().equals(elt.getClassName()) && "createWorkerEventLoopGroup".equals(elt.getMethodName())) {
                return WORKER;
            }
            if (NettyHttpServer.class.getName().equals(elt.getClassName()) && "createParentEventLoopGroup".equals(elt.getMethodName())) {
                return PARENT;
            }
        }
        return WORKER;
    }

    private record EventLoopGroupMetrics(AtomicInteger queueCounter,
                                         Counter taskCounter,
                                         Timer globalWaitTimeTimer,
                                         Timer globalExecutionTimer) {
        static EventLoopGroupMetrics create(MeterRegistry meterRegistry, String group) {
            Timer globalWaitTimeTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
                    .description("Global wait time spent in the event loop group queues.")
                    .tag(GROUP, group)
                    .publishPercentileHistogram()
                    .register(meterRegistry);
            Timer globalExecutionTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
                    .description("Global runnable execution time for the event loop group.")
                    .tag(GROUP, group)
                    .publishPercentileHistogram()
                    .register(meterRegistry);
            Counter taskCounter = Counter.builder(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
                    .tag(GROUP, group)
                    .register(meterRegistry);
            return new EventLoopGroupMetrics(new AtomicInteger(-1), taskCounter, globalWaitTimeTimer, globalExecutionTimer);
        }
    }

}
