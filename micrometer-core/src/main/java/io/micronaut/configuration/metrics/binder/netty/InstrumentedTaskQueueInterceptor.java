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

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.TaskQueueInterceptor;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.Queue;

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.PARENT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WORKER;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Intercepts Micronaut 5 Netty task queues so queue metrics are recorded for server event loops.
 *
 * @author nmikic
 * @since 5.0
 */
@Singleton
@BootstrapContextCompatible
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = FALSE, notEquals = FALSE)
@Requires(classes = TaskQueueInterceptor.class)
@Internal
final class InstrumentedTaskQueueInterceptor implements TaskQueueInterceptor {

    private final BeanProvider<NettyHttpServerConfiguration> serverConfigurationProvider;
    private final NettyQueueMetricsSupport queueMetricsSupport;

    InstrumentedTaskQueueInterceptor(BeanProvider<NettyHttpServerConfiguration> serverConfigurationProvider,
                                     NettyQueueMetricsSupport queueMetricsSupport) {
        this.serverConfigurationProvider = serverConfigurationProvider;
        this.queueMetricsSupport = queueMetricsSupport;
    }

    @Override
    public Queue<Runnable> wrapTaskQueue(String groupName, Queue<Runnable> original) {
        Optional<NettyHttpServerConfiguration> serverConfiguration = serverConfigurationProvider.find(Qualifiers.byName("default"));
        String workerGroupName = serverConfiguration
                .map(NettyHttpServerConfiguration::getWorker)
                .map(NettyHttpServerConfiguration.Worker::getName)
                .orElse(EventLoopGroupConfiguration.DEFAULT);
        String parentGroupName = serverConfiguration
                .map(NettyHttpServerConfiguration::getParent)
                .map(NettyHttpServerConfiguration.Parent::getName)
                .orElse(PARENT);
        if (parentGroupName.equals(groupName)) {
            return queueMetricsSupport.wrapTaskQueue(PARENT, original);
        }
        if (workerGroupName.equals(groupName)) {
            return queueMetricsSupport.wrapTaskQueue(WORKER, original);
        }
        return queueMetricsSupport.wrapTaskQueue(groupName, original);
    }
}
