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
package io.micronaut.http.server.netty;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.configuration.metrics.binder.netty.InstrumentedEventLoopTaskQueueFactory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;

/**
 * Factory for EpollEventLoopGroup.
 *
 * @author croudet
 * @since 2.0
 */
@Singleton
@Internal
@Replaces(factory = EpollEventLoopGroupFactory.class)
@Requires(beans = EpollEventLoopGroupFactory.class)
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = StringUtils.FALSE, notEquals = StringUtils.FALSE)
public class InstrumentedEpollEventLoopGroupFactory implements EventLoopGroupFactory {
    private final InstrumentedEventLoopTaskQueueFactory instrumentedEventLoopTaskQueueFactory;

    @Inject
    public InstrumentedEpollEventLoopGroupFactory(InstrumentedEventLoopTaskQueueFactory instrumentedEventLoopTaskQueueFactory) {
        this.instrumentedEventLoopTaskQueueFactory = instrumentedEventLoopTaskQueueFactory;
    }

    /**
     * Creates a EpollEventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return A EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, (Executor) null,
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory);
    }

    /**
     * Creates a EpollEventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return A EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, (Executor) threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory),
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory);
    }

    /**
     * Creates a EpollEventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return A EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, executor,
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory);
    }

    /**
     * Creates a default EpollEventLoopGroup.
     *
     * @param ioRatio The io ratio.
     * @return A EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(@Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(0, (Executor) null,
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory);
    }

    /**
     * Returns the server channel class.
     *
     * @return EpollServerSocketChannel.
     */
    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return EpollServerSocketChannel.class;
    }
}
