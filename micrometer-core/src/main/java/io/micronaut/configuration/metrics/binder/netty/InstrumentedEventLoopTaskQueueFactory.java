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
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.util.internal.PlatformDependent;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
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
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instrumented Event Loop Queue factory.
 * The global meters are registered when the meter registry is created, so building the
 * event loop group factory does not initialize the registry from inside HTTP client construction.
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
final class InstrumentedEventLoopTaskQueueFactory implements EventLoopTaskQueueFactory, MeterBinder {

    private static final AtomicInteger PARENT_COUNTER = new AtomicInteger(-1);
    private static final AtomicInteger WORKER_COUNTER = new AtomicInteger(-1);

    private final BeanProvider<MeterRegistry> meterRegistryProvider;

    /**
     * @param meterRegistryProvider the metric registry provider
     */
    public InstrumentedEventLoopTaskQueueFactory(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        for (String group : List.of(PARENT, WORKER)) {
            globalWaitTimeTimer(meterRegistry, group);
            globalExecutionTimer(meterRegistry, group);
            globalTaskCounter(meterRegistry, group);
        }
    }

    @Override
    public Queue<Runnable> newTaskQueue(int maxCapacity) {
        final String kind = findOrigin();
        final boolean parent = PARENT.equals(kind);
        final MeterRegistry meterRegistry = meterRegistryProvider.get();
        return new MonitoredQueue(parent ? PARENT_COUNTER.incrementAndGet() : WORKER_COUNTER.incrementAndGet(),
                meterRegistry,
                Tag.of(GROUP, kind),
                globalTaskCounter(meterRegistry, kind),
                globalWaitTimeTimer(meterRegistry, kind),
                globalExecutionTimer(meterRegistry, kind),
                maxCapacity == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue() : PlatformDependent.<Runnable>newMpscQueue(maxCapacity));
    }

    // Registering an existing meter id returns the meter that is already registered.
    private static Timer globalWaitTimeTimer(MeterRegistry meterRegistry, String group) {
        return Timer.builder(dot(NETTY, QUEUE, GLOBAL, WAIT_TIME))
                .description("Global wait time spent in the " + group + " Queues.")
                .tag(GROUP, group)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private static Timer globalExecutionTimer(MeterRegistry meterRegistry, String group) {
        return Timer.builder(dot(NETTY, QUEUE, GLOBAL, EXECUTION_TIME))
                .description("Global " + group + " runnable execution time.")
                .tag(GROUP, group)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private static Counter globalTaskCounter(MeterRegistry meterRegistry, String group) {
        return Counter.builder(dot(NETTY, QUEUE, GLOBAL, ELEMENT, COUNT))
                .tag(GROUP, group)
                .register(meterRegistry);
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

}
