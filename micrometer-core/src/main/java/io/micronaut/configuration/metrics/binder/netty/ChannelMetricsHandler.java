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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.LongAdder;

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ACTIVE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.BYTE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.CHANNEL;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.COUNT;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.ERROR;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.READ;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.TIME;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WRITTEN;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot;

/**
 * Metrics for Netty Channels.
 *
 * @author Christophe Roudet
 * @since 2.0
 */
@Sharable
@Internal
final class ChannelMetricsHandler extends ChannelDuplexHandler {

    /**
     * Channel metrics.
     */
    static final String CHANNEL_METRICS = "channel-metrics";

    private static final String ACTIVE_CHANNEL_TIMER = "active-channel-timer";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelMetricsHandler.class);

    private final BeanProvider<MeterRegistry> meterRegistryProvider;

    private final Counter bytesRead;
    private final Counter bytesWritten;
    private final Counter channelCount;
    private final Counter channelErrorCount;
    private final LongAdder activeChannelCount;
    private final Timer activeChannelTimer;

    ChannelMetricsHandler(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
        activeChannelCount = meterRegistryProvider.get().gauge(dot(NETTY, CHANNEL, COUNT, ACTIVE), Tags.of(CHANNEL, ACTIVE), new LongAdder());
        channelCount = Counter.builder(dot(NETTY, CHANNEL, COUNT))
                .tag(CHANNEL, COUNT)
                .register(meterRegistryProvider.get());
        channelErrorCount = Counter.builder(dot(NETTY, CHANNEL, COUNT))
                .tag(CHANNEL, ERROR)
                .register(meterRegistryProvider.get());
        bytesRead = Counter.builder(dot(NETTY, CHANNEL, BYTE))
                .tag(BYTE, READ)
                .baseUnit(BaseUnits.BYTES)
                .register(meterRegistryProvider.get());
        bytesWritten = Counter.builder(dot(NETTY, CHANNEL, BYTE))
                .tag(BYTE, WRITTEN)
                .baseUnit(BaseUnits.BYTES)
                .register(meterRegistryProvider.get());
        activeChannelTimer = Timer.builder(dot(NETTY, CHANNEL, TIME))
                .tag(ACTIVE, TIME)
                .publishPercentileHistogram()
                .register(meterRegistryProvider.get());
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        channelCount.increment();
        activeChannelCount.increment();
        ctx.pipeline().addAfter(
                CHANNEL_METRICS,
                ACTIVE_CHANNEL_TIMER,
                new ActiveChannelTimerHandler());
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        activeChannelCount.decrement();
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            final ByteBuf buffer = (ByteBuf) msg;
            if (buffer.readableBytes() > 0) {
                bytesRead.increment(buffer.readableBytes());
            }
        } else if (msg instanceof ByteBufHolder) {
            final ByteBufHolder buffer = (ByteBufHolder) msg;
            if (buffer.content().readableBytes() > 0) {
                bytesRead.increment(buffer.content().readableBytes());
            }
        } else {
            LOGGER.warn("Message type not supported: {}", msg.getClass());
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof ByteBuf) {
            final ByteBuf buffer = (ByteBuf) msg;
            if (buffer.readableBytes() > 0) {
                bytesWritten.increment(buffer.readableBytes());
            }
        } else if (msg instanceof ByteBufHolder) {
            final ByteBufHolder buffer = (ByteBufHolder) msg;
            if (buffer.content().readableBytes() > 0) {
                bytesWritten.increment(buffer.content().readableBytes());
            }
        } else {
            LOGGER.warn("Message type not supported: {}", msg.getClass());
        }

        ctx.write(msg, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        channelErrorCount.increment();
        ctx.fireExceptionCaught(cause);
    }

    private class ActiveChannelTimerHandler extends ChannelInboundHandlerAdapter {
        private final Timer.Sample start;

        ActiveChannelTimerHandler() {
            start = Timer.start(meterRegistryProvider.get());
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            start.stop(activeChannelTimer);
            ctx.pipeline().remove(this);
            ctx.fireChannelUnregistered();
        }
    }
}
