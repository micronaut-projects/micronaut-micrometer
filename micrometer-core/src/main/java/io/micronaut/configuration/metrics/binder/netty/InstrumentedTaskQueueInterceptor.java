/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.channel.TaskQueueInterceptor;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
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

@Singleton
@Internal
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = FALSE, notEquals = FALSE)
final class InstrumentedTaskQueueInterceptor implements TaskQueueInterceptor {
    private final BeanProvider<MeterRegistry> meterRegistryProvider;
    private final Map<String, EventLoopMetrics> eventLoopMetrics = new ConcurrentHashMap<>();

    /**
     * @param meterRegistryProvider the metric registry provider
     */
    public InstrumentedTaskQueueInterceptor(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public Queue<Runnable> wrapTaskQueue(String groupName, Queue<Runnable> original) {
        EventLoopMetrics metrics = eventLoopMetrics.computeIfAbsent(groupName, EventLoopMetrics::new);
        return new MonitoredQueue(metrics.counter.getAndIncrement(),
                meterRegistryProvider.get(),
                Tag.of(GROUP, metrics.groupName),
                metrics.taskCounter,
                metrics.globalWaitTimeTimer,
                metrics.globalExecutionTimer,
                original);
    }

    private class EventLoopMetrics {
        final String groupName;
        final Counter taskCounter;
        final Timer globalWaitTimeTimer;
        final Timer globalExecutionTimer;
        final AtomicInteger counter = new AtomicInteger();

        EventLoopMetrics(String groupName) {
            this.groupName = groupName;
            taskCounter = Counter.builder(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
                .tag(GROUP, groupName)
                .register(meterRegistryProvider.get());
            globalWaitTimeTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
                .description("Global wait time spent in the queues.")
                .tag(GROUP, groupName)
                .publishPercentileHistogram()
                .register(meterRegistryProvider.get());
            globalExecutionTimer = Timer.builder(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
                .description("Global runnable execution time.")
                .tag(GROUP, groupName)
                .publishPercentileHistogram()
                .register(meterRegistryProvider.get());
        }
    }
}
