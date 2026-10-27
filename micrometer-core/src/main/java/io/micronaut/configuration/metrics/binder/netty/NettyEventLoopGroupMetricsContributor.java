/*
 * Copyright 2017-2026 original authors
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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micronaut.configuration.metrics.binder.executor.ExecutorServiceMetricsContributor;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Binds executor-style gauges for Netty event loop groups.
 * Groups created during context initialization are bound after startup to avoid
 * initializing the meter registry from inside HTTP client construction.
 *
 * @since 6.0.0
 */
@Singleton
@Requires(classes = EventLoopGroup.class)
@Internal
final class NettyEventLoopGroupMetricsContributor implements ExecutorServiceMetricsContributor, ApplicationEventListener<StartupEvent> {

    private final List<PendingBinding> pendingBindings = new ArrayList<>();
    private boolean started;
    private boolean starting;

    @Override
    public boolean supports(ExecutorService executorService) {
        return executorService instanceof EventLoopGroup;
    }

    @Override
    public ExecutorService bindTo(BeanProvider<MeterRegistry> meterRegistryProvider,
                                  ExecutorService executorService, String name, List<Tag> tags) {
        synchronized (pendingBindings) {
            if (!started) {
                pendingBindings.add(new PendingBinding(meterRegistryProvider, executorService, name, List.copyOf(tags)));
                return executorService;
            }
        }
        return bindTo(meterRegistryProvider.get(), executorService, name, tags);
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        synchronized (pendingBindings) {
            if (started || starting) {
                return;
            }
            starting = true;
        }
        try {
            while (true) {
                List<PendingBinding> bindings;
                synchronized (pendingBindings) {
                    if (pendingBindings.isEmpty()) {
                        started = true;
                        return;
                    }
                    bindings = List.copyOf(pendingBindings);
                    pendingBindings.clear();
                }
                // Resolving the registry may create more event loop groups. Keep collecting
                // them until binding finishes, and never resolve beans while holding the lock.
                for (PendingBinding binding : bindings) {
                    if (!binding.executorService().isShutdown()) {
                        bindTo(binding.meterRegistryProvider().get(), binding.executorService(), binding.name(), binding.tags());
                    }
                }
            }
        } finally {
            synchronized (pendingBindings) {
                starting = false;
            }
        }
    }

    @Override
    public ExecutorService bindTo(MeterRegistry meterRegistry, ExecutorService executorService, String name, List<Tag> tags) {
        EventLoopGroup eventLoopGroup = (EventLoopGroup) executorService;
        Iterable<Tag> meterTags = Tags.concat(tags, "name", name);
        Gauge.builder("executor.queued", eventLoopGroup, NettyEventLoopGroupMetricsContributor::pendingTasks)
                .tags(meterTags)
                .description("The approximate number of tasks that are queued for execution.")
                .register(meterRegistry);
        Gauge.builder("executor.pool.size", eventLoopGroup, NettyEventLoopGroupMetricsContributor::poolSize)
                .tags(meterTags)
                .description("The current number of threads in the pool.")
                .baseUnit("threads")
                .register(meterRegistry);
        return executorService;
    }

    private static double pendingTasks(EventLoopGroup eventLoopGroup) {
        int pendingTasks = 0;
        for (EventExecutor eventExecutor : eventLoopGroup) {
            if (eventExecutor instanceof SingleThreadEventExecutor singleThreadEventExecutor) {
                pendingTasks += singleThreadEventExecutor.pendingTasks();
            }
        }
        return pendingTasks;
    }

    private static double poolSize(EventLoopGroup eventLoopGroup) {
        int poolSize = 0;
        for (EventExecutor ignored : eventLoopGroup) {
            poolSize++;
        }
        return poolSize;
    }

    private record PendingBinding(BeanProvider<MeterRegistry> meterRegistryProvider,
                                  ExecutorService executorService, String name, List<Tag> tags) {
    }
}
