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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.channel.TaskQueueInterceptor;
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
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.QUEUE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WAIT_TIME;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instruments Netty event loop task queues created through the event loop registry.
 *
 * @author Jonas Konrad
 * @since 5.0.0
 */
@Singleton
@BootstrapContextCompatible
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = FALSE, notEquals = FALSE)
@Requires(classes = TaskQueueInterceptor.class)
@Internal
final class InstrumentedEventLoopTaskQueueInterceptor implements TaskQueueInterceptor {

    private final BeanProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider;
    private final ConcurrentMap<String, GroupMetrics> groupMetrics = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> groupCounters = new ConcurrentHashMap<>();

    InstrumentedEventLoopTaskQueueInterceptor(BeanProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public Queue<Runnable> wrapTaskQueue(String groupName, Queue<Runnable> original) {
        GroupMetrics metrics = groupMetrics.computeIfAbsent(groupName, this::createGroupMetrics);
        int index = groupCounters.computeIfAbsent(groupName, ignored -> new AtomicInteger(-1)).incrementAndGet();
        return new MonitoredQueue(
            index,
            meterRegistryProvider.get(),
            Tag.of(GROUP, groupName),
            metrics.taskCounter(),
            metrics.waitTimeTimer(),
            metrics.executionTimer(),
            original
        );
    }

    private GroupMetrics createGroupMetrics(String groupName) {
        io.micrometer.core.instrument.MeterRegistry meterRegistry = meterRegistryProvider.get();
        Timer waitTimeTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
            .description("Global wait time spent in the event loop queues.")
            .tag(GROUP, groupName)
            .publishPercentileHistogram()
            .register(meterRegistry);
        Timer executionTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
            .description("Global runnable execution time for the event loop queues.")
            .tag(GROUP, groupName)
            .publishPercentileHistogram()
            .register(meterRegistry);
        Counter taskCounter = Counter.builder(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
            .tag(GROUP, groupName)
            .register(meterRegistry);
        return new GroupMetrics(waitTimeTimer, executionTimer, taskCounter);
    }

    private record GroupMetrics(Timer waitTimeTimer, Timer executionTimer, Counter taskCounter) {
    }
}
