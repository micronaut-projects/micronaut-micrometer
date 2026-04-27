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
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.netty.util.internal.PlatformDependent;
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

/**
 * Shared metrics registration and queue wrapping support for Netty task queues.
 *
 * @author nmikic
 * @since 5.0
 */
@Singleton
@BootstrapContextCompatible
@Internal
final class NettyQueueMetricsSupport {

    private final BeanProvider<MeterRegistry> meterRegistryProvider;
    private final ConcurrentMap<String, GroupMetrics> groupMetrics = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> groupCounters = new ConcurrentHashMap<>();

    NettyQueueMetricsSupport(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    Queue<Runnable> newTaskQueue(String kind, int maxCapacity) {
        Queue<Runnable> queue = maxCapacity == Integer.MAX_VALUE
                ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxCapacity);
        return wrapTaskQueue(kind, queue);
    }

    Queue<Runnable> wrapTaskQueue(String kind, Queue<Runnable> queue) {
        GroupMetrics metrics = groupMetrics.computeIfAbsent(kind, this::createGroupMetrics);
        int index = groupCounters.computeIfAbsent(kind, ignored -> new AtomicInteger(-1)).incrementAndGet();
        return new MonitoredQueue(
                index,
                metrics.meterRegistry,
                Tag.of(GROUP, kind),
                metrics.taskCounter,
                metrics.globalWaitTimeTimer,
                metrics.globalExecutionTimer,
                queue
        );
    }

    private GroupMetrics createGroupMetrics(String kind) {
        MeterRegistry registry = meterRegistryProvider.get();
        Counter taskCounter = Counter.builder(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
                .tag(GROUP, kind)
                .register(registry);
        Timer waitTimeTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
                .description(waitTimeDescription(kind))
                .tag(GROUP, kind)
                .publishPercentileHistogram()
                .register(registry);
        Timer executionTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
                .description(executionTimeDescription(kind))
                .tag(GROUP, kind)
                .publishPercentileHistogram()
                .register(registry);
        return new GroupMetrics(registry, taskCounter, waitTimeTimer, executionTimer);
    }

    private static String waitTimeDescription(String kind) {
        if (PARENT.equals(kind)) {
            return "Global wait time spent in the parent Queues.";
        }
        if (WORKER.equals(kind)) {
            return "Global wait time spent in the worker Queues.";
        }
        return "Global wait time spent in the event loop queues.";
    }

    private static String executionTimeDescription(String kind) {
        if (PARENT.equals(kind)) {
            return "Global parent runnable execution time.";
        }
        if (WORKER.equals(kind)) {
            return "Global worker runnable execution time.";
        }
        return "Global runnable execution time for the event loop queues.";
    }

    private record GroupMetrics(MeterRegistry meterRegistry,
                                Counter taskCounter,
                                Timer globalWaitTimeTimer,
                                Timer globalExecutionTimer) {
    }
}
