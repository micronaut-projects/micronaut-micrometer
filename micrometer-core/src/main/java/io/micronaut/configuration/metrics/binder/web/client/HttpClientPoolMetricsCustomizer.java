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
package io.micronaut.configuration.metrics.binder.web.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.netty.NettyClientCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http2.Http2FrameCodec;

/**
 * Follows the connections and requests of the Netty HTTP client through the
 * {@link NettyClientCustomizer} hooks, and records them in {@link HttpClientPoolMetrics}.
 *
 * <ul>
 *     <li>A connection attempt starts when the client specializes the customizer for the
 *     bootstrap of a new connection, just before it connects.</li>
 *     <li>It is established when the stream pipeline of the connection is built, which is when the
 *     pool can use it (after the TLS handshake and the protocol negotiation).</li>
 *     <li>It ends when its channel closes. An attempt whose channel closes before it is established
 *     failed.</li>
 *     <li>A WebSocket connection is not a pool connection. Its attempt is abandoned once its
 *     initial pipeline, which has the WebSocket client handler, is built.</li>
 *     <li>A request is active from when its request pipeline is built until the handler added to
 *     that pipeline is removed, which the client does when the request completes, or when the
 *     channel of the request closes.</li>
 * </ul>
 *
 * @since 6.2.0
 */
@Internal
final class HttpClientPoolMetricsCustomizer implements NettyClientCustomizer {
    private final HttpClientPoolMetrics metrics;

    HttpClientPoolMetricsCustomizer(HttpClientPoolMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public NettyClientCustomizer specializeForBootstrap(Bootstrap bootstrap) {
        ChannelHandler handler = bootstrap.config().handler();
        if (handler == null) {
            return this;
        }
        HttpClientPoolMetrics.ConnectionAttempt attempt = metrics.beginConnectionAttempt();
        bootstrap.handler(new AttemptInitializer(attempt, handler));
        return new AttemptCustomizer(metrics, attempt);
    }

    static String protocol(Channel channel) {
        ChannelHandler connectionHandler = channel.pipeline().get(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION);
        if (connectionHandler == null) {
            return HttpClientPoolMetrics.HTTP_1_1;
        }
        return connectionHandler instanceof Http2FrameCodec ? HttpClientPoolMetrics.HTTP_2 : HttpClientPoolMetrics.HTTP_3;
    }

    static void addRequestHandler(HttpClientPoolMetrics metrics, Channel channel) {
        channel.pipeline().addLast(new ActiveRequestHandler(metrics));
    }

    /**
     * Ends the attempt when the channel of the connection attempt closes, including a channel that
     * fails to connect, then lets the initializer of the client initialize the channel.
     */
    private static final class AttemptInitializer extends ChannelInitializer<Channel> {
        private final HttpClientPoolMetrics.ConnectionAttempt attempt;
        private final ChannelHandler handler;

        private AttemptInitializer(HttpClientPoolMetrics.ConnectionAttempt attempt, ChannelHandler handler) {
            this.attempt = attempt;
            this.handler = handler;
        }

        @Override
        protected void initChannel(Channel ch) {
            ch.closeFuture().addListener(future -> attempt.closed());
            ch.pipeline().addLast(handler);
        }
    }

    private static final class AttemptCustomizer implements NettyClientCustomizer {
        private final HttpClientPoolMetrics metrics;
        private final HttpClientPoolMetrics.ConnectionAttempt attempt;

        private AttemptCustomizer(HttpClientPoolMetrics metrics, HttpClientPoolMetrics.ConnectionAttempt attempt) {
            this.metrics = metrics;
            this.attempt = attempt;
        }

        @Override
        public NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            if (role == ChannelRole.CONNECTION) {
                return new ConnectionCustomizer(metrics, attempt, channel);
            }
            return this;
        }
    }

    private static final class ConnectionCustomizer implements NettyClientCustomizer {
        private final HttpClientPoolMetrics metrics;
        private final HttpClientPoolMetrics.ConnectionAttempt attempt;
        private final Channel channel;

        private ConnectionCustomizer(HttpClientPoolMetrics metrics, HttpClientPoolMetrics.ConnectionAttempt attempt, Channel channel) {
            this.metrics = metrics;
            this.attempt = attempt;
            this.channel = channel;
        }

        @Override
        public NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            if (role == ChannelRole.HTTP2_STREAM) {
                return new StreamCustomizer(metrics, channel);
            }
            return this;
        }

        @Override
        public void onInitialPipelineBuilt() {
            if (channel.pipeline().get(ChannelPipelineCustomizer.HANDLER_MICRONAUT_WEBSOCKET_CLIENT) != null) {
                attempt.abandoned();
            }
        }

        @Override
        public void onStreamPipelineBuilt() {
            attempt.established(protocol(channel));
            // the connection channel differs from the attempt channel for HTTP/3
            channel.closeFuture().addListener(future -> attempt.closed());
        }

        @Override
        public void onRequestPipelineBuilt() {
            addRequestHandler(metrics, channel);
        }
    }

    private static final class StreamCustomizer implements NettyClientCustomizer {
        private final HttpClientPoolMetrics metrics;
        private final Channel channel;

        private StreamCustomizer(HttpClientPoolMetrics metrics, Channel channel) {
            this.metrics = metrics;
            this.channel = channel;
        }

        @Override
        public void onRequestPipelineBuilt() {
            addRequestHandler(metrics, channel);
        }
    }

    /**
     * Counts a request as active while it is in the pipeline of the request.
     */
    private static final class ActiveRequestHandler extends ChannelHandlerAdapter {
        private final HttpClientPoolMetrics metrics;
        private boolean counted;

        private ActiveRequestHandler(HttpClientPoolMetrics metrics) {
            this.metrics = metrics;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            counted = true;
            metrics.requestStarted();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (counted) {
                counted = false;
                metrics.requestFinished();
            }
        }
    }
}
