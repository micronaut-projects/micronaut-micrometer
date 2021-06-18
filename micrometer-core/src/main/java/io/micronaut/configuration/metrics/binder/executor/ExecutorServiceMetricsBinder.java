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
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Instruments Micronaut related thread pools via Micrometer.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".executor.enabled", notEquals = StringUtils.FALSE)
public class ExecutorServiceMetricsBinder implements BeanCreatedEventListener<ExecutorService> {

    private final BeanProvider<MeterRegistry> meterRegistryProvider;

    /**
     * Default constructor.
     *
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
            InstrumentedExecutorService ies = (InstrumentedExecutorService) unwrapped;
            unwrapped = ies.getTarget();
        }
        // Netty EventLoopGroups require separate instrumentation.
        if (unwrapped.getClass().getName().startsWith("io.netty")) {
            return unwrapped;
        }
        MeterRegistry meterRegistry = meterRegistryProvider.get();
        BeanIdentifier beanIdentifier = event.getBeanIdentifier();

        List<Tag> tags = Collections.emptyList(); // allow tags?

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
}
