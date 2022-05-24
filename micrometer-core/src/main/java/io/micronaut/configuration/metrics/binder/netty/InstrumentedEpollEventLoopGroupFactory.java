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
import io.micronaut.http.netty.channel.EpollAvailabilityCondition;
import io.micronaut.http.netty.channel.EpollEventLoopGroupFactory;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Factory for Instrumented EpollEventLoopGroup.
 *
 * @author croudet
 * @since 2.0
 */
@Singleton
@Internal
@Replaces(bean = EpollEventLoopGroupFactory.class, named = EventLoopGroupFactory.NATIVE)
@Named(EventLoopGroupFactory.NATIVE)
@Requires(classes = Epoll.class, condition = EpollAvailabilityCondition.class)
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled", defaultValue = FALSE, notEquals = FALSE)
final class InstrumentedEpollEventLoopGroupFactory implements EventLoopGroupFactory {

    private final InstrumentedEventLoopTaskQueueFactory instrumentedEventLoopTaskQueueFactory;

    /**
     * @param factory InstrumentedEventLoopTaskQueueFactory
     */
    public InstrumentedEpollEventLoopGroupFactory(InstrumentedEventLoopTaskQueueFactory factory) {
        this.instrumentedEventLoopTaskQueueFactory = factory;
    }

    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, null,
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory);
    }

    @Override
    public EventLoopGroup createEventLoopGroup(int threads,
                                               ThreadFactory threadFactory,
                                               @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory),
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory);
    }

    @Override
    public EventLoopGroup createEventLoopGroup(int threads,
                                               Executor executor,
                                               @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, executor,
                DefaultEventExecutorChooserFactory.INSTANCE,
                DefaultSelectStrategyFactory.INSTANCE,
                RejectedExecutionHandlers.reject(),
                instrumentedEventLoopTaskQueueFactory);
    }

    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return EpollServerSocketChannel.class;
    }

    @NonNull
    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return EpollSocketChannel.class;
    }

    @Override
    public boolean isNative() {
        return true;
    }
}
