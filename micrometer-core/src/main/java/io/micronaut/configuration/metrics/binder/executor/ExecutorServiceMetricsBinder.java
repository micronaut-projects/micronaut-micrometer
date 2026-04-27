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
package io.micronaut.configuration.metrics.binder.executor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;
import jakarta.inject.Singleton;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instruments Micronaut related thread pools via Micrometer.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".executor.enabled", notEquals = FALSE)
public class ExecutorServiceMetricsBinder implements BeanCreatedEventListener<ExecutorService> {

    private static final String THREAD_PER_TASK_EXECUTOR = "java.util.concurrent.ThreadPerTaskExecutor";
    private static final String EVENT_LOOP_GROUP_CLASS_NAME = "io.netty.channel.EventLoopGroup";
    private static final String SINGLE_THREAD_EVENT_EXECUTOR_CLASS_NAME = "io.netty.util.concurrent.SingleThreadEventExecutor";
    private static final String PENDING_TASKS_METHOD_NAME = "pendingTasks";
    private static final ClassValue<Boolean> EVENT_LOOP_GROUP_TYPES = new NettyTypeMatcher(EVENT_LOOP_GROUP_CLASS_NAME);
    private static final ClassValue<Boolean> SINGLE_THREAD_EVENT_EXECUTOR_TYPES = new NettyTypeMatcher(SINGLE_THREAD_EVENT_EXECUTOR_CLASS_NAME);
    private static final ClassValue<Optional<Method>> PENDING_TASKS_METHODS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod(PENDING_TASKS_METHOD_NAME));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }
    };

    private final BeanProvider<MeterRegistry> meterRegistryProvider;

    /**
     * @param meterRegistryProvider The meter registry provider
     */
    public ExecutorServiceMetricsBinder(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public ExecutorService onCreated(BeanCreatedEvent<ExecutorService> event) {
        ExecutorService executorService = event.getBean();
        // have to unwrap any Micronaut instrumentations to get the target
        ExecutorService unwrapped = executorService;
        while (unwrapped instanceof InstrumentedExecutorService) {
            unwrapped = ((InstrumentedExecutorService) unwrapped).getTarget();
        }
        // ExecutorServiceMetrics does not provide metrics for virtual threads
        if (unwrapped.getClass().getName().equals(THREAD_PER_TASK_EXECUTOR)) {
            return executorService;
        }

        MeterRegistry meterRegistry = meterRegistryProvider.get();
        BeanIdentifier beanIdentifier = event.getBeanIdentifier();

        List<Tag> tags = Collections.emptyList(); // allow tags?

        // EventLoopGroups need to stay unwrapped so the bean remains assignable as EventLoopGroup.
        if (isEventLoopGroup(unwrapped)) {
            bindEventLoopGroupMetrics(meterRegistry, unwrapped, beanIdentifier.getName(), tags);
            return unwrapped;
        }

        // bind the service metrics
        new ExecutorServiceMetrics(unwrapped, beanIdentifier.getName(), tags).bindTo(meterRegistry);

        // allow timing
        final Timer timer = meterRegistry.timer("executor", Tags.concat(tags , "name", beanIdentifier.getName()));
        if (executorService instanceof ScheduledExecutorService) {
            return new InstrumentedScheduledExecutorService() {

                @Override
                public ScheduledExecutorService getTarget() {
                    return (ScheduledExecutorService) executorService;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> task) {
                    return timer.wrap(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return timer.wrap(command);
                }
            };
        } else {
            return new InstrumentedExecutorService() {
                @Override
                public ExecutorService getTarget() {
                    return executorService;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> task) {
                    return timer.wrap(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return timer.wrap(command);
                }
            };
        }
    }

    private static void bindEventLoopGroupMetrics(MeterRegistry meterRegistry,
                                                  ExecutorService eventLoopGroup,
                                                  String name,
                                                  List<Tag> tags) {
        Iterable<Tag> meterTags = Tags.concat(tags, "name", name);
        Gauge.builder("executor.queued", eventLoopGroup, ExecutorServiceMetricsBinder::pendingTasks)
                .tags(meterTags)
                .description("The approximate number of tasks that are queued for execution.")
                .register(meterRegistry);
        Gauge.builder("executor.pool.size", eventLoopGroup, ExecutorServiceMetricsBinder::poolSize)
                .tags(meterTags)
                .description("The current number of threads in the pool.")
                .baseUnit("threads")
                .register(meterRegistry);
    }

    private static double pendingTasks(ExecutorService eventLoopGroup) {
        if (!(eventLoopGroup instanceof Iterable<?> iterable)) {
            return 0;
        }
        int pendingTasks = 0;
        for (Object eventExecutor : iterable) {
            if (isSingleThreadEventExecutor(eventExecutor)) {
                pendingTasks += invokePendingTasks(eventExecutor);
            }
        }
        return pendingTasks;
    }

    private static double poolSize(ExecutorService eventLoopGroup) {
        if (!(eventLoopGroup instanceof Iterable<?> iterable)) {
            return 0;
        }
        int poolSize = 0;
        Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            poolSize++;
        }
        return poolSize;
    }

    private static boolean isEventLoopGroup(ExecutorService executorService) {
        return EVENT_LOOP_GROUP_TYPES.get(executorService.getClass());
    }

    private static boolean isSingleThreadEventExecutor(Object eventExecutor) {
        return SINGLE_THREAD_EVENT_EXECUTOR_TYPES.get(eventExecutor.getClass());
    }

    private static int invokePendingTasks(Object eventExecutor) {
        Optional<Method> pendingTasksMethod = PENDING_TASKS_METHODS.get(eventExecutor.getClass());
        if (pendingTasksMethod.isEmpty()) {
            return 0;
        }
        try {
            return ((Number) pendingTasksMethod.get().invoke(eventExecutor)).intValue();
        } catch (IllegalAccessException | InvocationTargetException e) {
            return 0;
        }
    }

    private static final class NettyTypeMatcher extends ClassValue<Boolean> {
        private final String className;

        private NettyTypeMatcher(String className) {
            this.className = className;
        }

        @Override
        protected Boolean computeValue(Class<?> type) {
            Class<?> resolvedType = resolveType(type.getClassLoader(), className);
            return resolvedType != null && resolvedType.isAssignableFrom(type);
        }

        private static Class<?> resolveType(ClassLoader classLoader, String className) {
            try {
                return Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException e) {
                ClassLoader fallbackClassLoader = ExecutorServiceMetricsBinder.class.getClassLoader();
                if (fallbackClassLoader == classLoader) {
                    return null;
                }
                try {
                    return Class.forName(className, false, fallbackClassLoader);
                } catch (ClassNotFoundException ignored) {
                    return null;
                }
            }
        }
    }
}
