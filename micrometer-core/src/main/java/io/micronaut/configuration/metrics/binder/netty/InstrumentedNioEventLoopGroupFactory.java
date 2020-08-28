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

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.NioEventLoopGroupFactory;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;

/**
 * Factory for Instrumented NioEventLoopGroup.
 *
 * @author croudet
 * @since 2.0
 */
@Singleton
@Internal
@Replaces(factory = NioEventLoopGroupFactory.class)
@Requires(beans = NioEventLoopGroupFactory.class)
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = StringUtils.FALSE, notEquals = StringUtils.FALSE)
final class InstrumentedNioEventLoopGroupFactory implements EventLoopGroupFactory {
    private final InstrumentedEventLoopTaskQueueFactory instrumentedEventLoopTaskQueueFactory;

    /**
     * Creates an InstrumentedNioEventLoopGroupFactory.
     *
     * @param instrumentedEventLoopTaskQueueFactory An InstrumentedEventLoopTaskQueueFactory
     */
    @Inject
    public InstrumentedNioEventLoopGroupFactory(InstrumentedEventLoopTaskQueueFactory instrumentedEventLoopTaskQueueFactory) {
        this.instrumentedEventLoopTaskQueueFactory = instrumentedEventLoopTaskQueueFactory;
    }

    /**
     * Creates a NioEventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(threads, (Executor) null, (EventExecutorChooserFactory) null,
                SelectorProvider.provider(),
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory), ioRatio);
    }

    /**
     * Creates a NioEventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(threads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory),
                DefaultEventExecutorChooserFactory.INSTANCE,
                SelectorProvider.provider(),
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory), ioRatio);
    }

    /**
     * Creates a NioEventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(threads, executor,
                DefaultEventExecutorChooserFactory.INSTANCE,
                SelectorProvider.provider(),
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory), ioRatio);
    }

    /**
     * Creates a default NioEventLoopGroup.
     *
     * @param ioRatio The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(@Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(0, (Executor) null,
                DefaultEventExecutorChooserFactory.INSTANCE,
                SelectorProvider.provider(),
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory), ioRatio);
    }

    /**
     * Returns the server channel class.
     *
     * @return NioServerSocketChannel.
     */
    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return NioServerSocketChannel.class;
    }

    @NonNull
    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return NioSocketChannel.class;
    }

    private static NioEventLoopGroup withIoRatio(NioEventLoopGroup group, @Nullable Integer ioRatio) {
        if (ioRatio != null) {
            group.setIoRatio(ioRatio);
        }
        return group;
    }
}
