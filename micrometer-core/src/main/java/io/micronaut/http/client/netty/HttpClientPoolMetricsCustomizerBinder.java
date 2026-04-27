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
package io.micronaut.http.client.netty;

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Registers HTTP client pool connection lifecycle instrumentation.
 */
@Singleton
@Internal
@RequiresMetrics
@Requires(classes = NettyClientCustomizer.Registry.class)
@Requires(property = MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled", defaultValue = FALSE, notEquals = FALSE)
final class HttpClientPoolMetricsCustomizerBinder implements BeanCreatedEventListener<NettyClientCustomizer.Registry> {
    private final HttpClientPoolMetricsRecorder recorder;

    HttpClientPoolMetricsCustomizerBinder(HttpClientPoolMetricsRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public NettyClientCustomizer.Registry onCreated(BeanCreatedEvent<NettyClientCustomizer.Registry> event) {
        NettyClientCustomizer.Registry registry = event.getBean();
        registry.register(new MetricsCustomizer(recorder, null, null));
        return registry;
    }

    private record MetricsCustomizer(
        HttpClientPoolMetricsRecorder recorder,
        @Nullable Channel channel,
        HttpClientPoolMetricsRecorder.@Nullable ConnectionAttempt attempt
    ) implements NettyClientCustomizer {

        @Override
        public NettyClientCustomizer specializeForBootstrap(Bootstrap bootstrap) {
            return new MetricsCustomizer(recorder, null, recorder.beginConnectionAttempt());
        }

        @Override
        public NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            if (role == ChannelRole.CONNECTION && attempt != null) {
                channel.closeFuture().addListener(future -> recorder.connectionClosed(attempt));
                return new MetricsCustomizer(recorder, channel, attempt);
            }
            return this;
        }

        @Override
        public void onStreamPipelineBuilt() {
            if (channel != null && attempt != null) {
                recorder.connectionEstablished(attempt);
            }
        }
    }
}
