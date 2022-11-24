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

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.netty.NettyServerCustomizer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.Channel;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.binder.netty.ChannelMetricsHandler.CHANNEL_METRICS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Adds Netty's metrics handler to the pipeline.
 *
 * @author croudet
 * @since 2.0
 */
@Singleton
@Internal
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.channels.enabled", defaultValue = FALSE, notEquals = FALSE)
@Requires(classes = EmbeddedServer.class)
final class NettyMetricsPipelineBinder implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {

    private final ChannelMetricsHandler metricsHandler;

    /**
     * Builds a NettyMetricsPipelineCustomizer that will add channel metrics.
     *
     * @param meterRegistryProvider The metrics registry provider.
     */
    NettyMetricsPipelineBinder(BeanProvider<MeterRegistry> meterRegistryProvider) {
        metricsHandler = new ChannelMetricsHandler(meterRegistryProvider);
    }

    @Override
    public NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
        NettyServerCustomizer.Registry registry = event.getBean();
        registry.register(new MetricsCustomizer(null, metricsHandler));
        return registry;
    }

    private record MetricsCustomizer(Channel channel,
                                     ChannelMetricsHandler metricsHandler) implements NettyServerCustomizer {

        @Override
        public NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            if (role == ChannelRole.CONNECTION) {
                return new MetricsCustomizer(channel, metricsHandler);
            }
            return this;
        }

        @Override
        public void onStreamPipelineBuilt() {
            if (channel != null) {
                channel.pipeline().addFirst(CHANNEL_METRICS, metricsHandler);
            }
        }
    }
}
