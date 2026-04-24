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

    private final AtomicInteger parentCounter = new AtomicInteger(-1);
    private final AtomicInteger workerCounter = new AtomicInteger(-1);
    private final BeanProvider<MeterRegistry> meterRegistryProvider;

    private volatile MeterReferences meterReferences;

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
        MeterReferences currentMeterReferences = initializeMeters();
        boolean parent = PARENT.equals(kind);
        return new MonitoredQueue(
                parent ? parentCounter.incrementAndGet() : workerCounter.incrementAndGet(),
                currentMeterReferences.meterRegistry,
                Tag.of(GROUP, kind),
                parent ? currentMeterReferences.parentTaskCounter : currentMeterReferences.workerTaskCounter,
                parent ? currentMeterReferences.globalParentWaitTimeTimer : currentMeterReferences.globalWorkerWaitTimeTimer,
                parent ? currentMeterReferences.globalParentExecutionTimer : currentMeterReferences.globalWorkerExecutionTimer,
                queue
        );
    }

    private MeterReferences initializeMeters() {
        MeterReferences currentMeterReferences = meterReferences;
        if (currentMeterReferences != null) {
            return currentMeterReferences;
        }
        synchronized (this) {
            if (meterReferences != null) {
                return meterReferences;
            }
            MeterRegistry registry = meterRegistryProvider.get();
            meterReferences = new MeterReferences(
                    registry,
                    Counter.builder(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
                            .tag(GROUP, PARENT)
                            .register(registry),
                    Counter.builder(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
                            .tag(GROUP, WORKER)
                            .register(registry),
                    Timer.builder(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
                    .description("Global wait time spent in the parent Queues.")
                    .tag(GROUP, PARENT)
                    .publishPercentileHistogram()
                    .register(registry),
                    Timer.builder(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
                    .description("Global parent runnable execution time.")
                    .tag(GROUP, PARENT)
                    .publishPercentileHistogram()
                    .register(registry),
                    Timer.builder(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
                    .description("Global wait time spent in the worker Queues.")
                    .tag(GROUP, WORKER)
                    .publishPercentileHistogram()
                    .register(registry),
                    Timer.builder(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
                    .description("Global worker runnable execution time.")
                    .tag(GROUP, WORKER)
                    .publishPercentileHistogram()
                    .register(registry)
            );
            return meterReferences;
        }
    }

    private record MeterReferences(MeterRegistry meterRegistry,
                                   Counter parentTaskCounter,
                                   Counter workerTaskCounter,
                                   Timer globalParentWaitTimeTimer,
                                   Timer globalParentExecutionTimer,
                                   Timer globalWorkerWaitTimeTimer,
                                   Timer globalWorkerExecutionTimer) {
    }
}
