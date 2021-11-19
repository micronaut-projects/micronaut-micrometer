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
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.KQueueAvailabilityCondition;
import io.micronaut.http.netty.channel.KQueueEventLoopGroupFactory;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Factory for Instrumented KQueueEventLoopGroup.
 *
 * @author croudet
 * @since 2.0
 */
@Singleton
@Internal
@Replaces(bean = KQueueEventLoopGroupFactory.class, named = EventLoopGroupFactory.NATIVE)
@Named(EventLoopGroupFactory.NATIVE)
@Requires(classes = KQueue.class, condition = KQueueAvailabilityCondition.class)
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = StringUtils.FALSE, notEquals = StringUtils.FALSE)
final class InstrumentedKQueueEventLoopGroupFactory implements EventLoopGroupFactory {
    private final InstrumentedEventLoopTaskQueueFactory instrumentedEventLoopTaskQueueFactory;

    /**
     * Creates an InstrumentedKQueueEventLoopGroupFactory.
     *
     * @param instrumentedEventLoopTaskQueueFactory An InstrumentedEventLoopTaskQueueFactory
     */
    @Inject
    public InstrumentedKQueueEventLoopGroupFactory(InstrumentedEventLoopTaskQueueFactory instrumentedEventLoopTaskQueueFactory) {
        this.instrumentedEventLoopTaskQueueFactory = instrumentedEventLoopTaskQueueFactory;
    }

    /**
     * Creates a KQueueEventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return withIoRatio(new KQueueEventLoopGroup(threads, (Executor) null,
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory), ioRatio);
    }

    /**
     * Creates a KQueueEventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return withIoRatio(new KQueueEventLoopGroup(threads, (Executor) threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory),
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory), ioRatio);
    }

    /**
     * Creates a KQueueEventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return withIoRatio(new KQueueEventLoopGroup(threads, executor,
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory), ioRatio);
    }

    @Override
    public boolean isNative() {
        return true;
    }

    /**
     * Returns the server channel class.
     *
     * @return KQueueServerSocketChannel.
     */
    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return KQueueServerSocketChannel.class;
    }

    @NonNull
    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return KQueueSocketChannel.class;
    }

    private static KQueueEventLoopGroup withIoRatio(KQueueEventLoopGroup group, @Nullable Integer ioRatio) {
        if (ioRatio != null) {
            group.setIoRatio(ioRatio);
        }
        return group;
    }

}
