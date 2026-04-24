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

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.netty.channel.EventLoopTaskQueueFactory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Queue;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.PARENT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WORKER;
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
final class InstrumentedEventLoopTaskQueueFactory implements EventLoopTaskQueueFactory {

    private final NettyQueueMetricsSupport queueMetricsSupport;

    /**
     * @param queueMetricsSupport Shared queue metrics support
     */
    public InstrumentedEventLoopTaskQueueFactory(NettyQueueMetricsSupport queueMetricsSupport) {
        this.queueMetricsSupport = queueMetricsSupport;
    }

    @Override
    public Queue<Runnable> newTaskQueue(int maxCapacity) {
        return queueMetricsSupport.newTaskQueue(findOrigin(), maxCapacity);
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
