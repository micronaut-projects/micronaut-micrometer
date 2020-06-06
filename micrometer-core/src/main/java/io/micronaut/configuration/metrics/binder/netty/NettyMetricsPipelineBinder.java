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

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.runtime.server.EmbeddedServer;

/**
 * Adds netty's metrics handler to the pipeline.
 *
 * @author croudet
 * @since 2.0
 */
@Singleton
@Internal
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".netty.channels.enabled", defaultValue = StringUtils.FALSE, notEquals = StringUtils.FALSE)
@Requires(classes = EmbeddedServer.class)
final class NettyMetricsPipelineBinder implements BeanCreatedEventListener<ChannelPipelineCustomizer> {
    private final ChannelMetricsHandler metricsHandler;

    /**
     * Builds a NettyMetricsPipelineCustomizer that will add channel metrics.
     *
     * @param meterRegistryProvider The metrics registry provider.
     */
    @Inject
    NettyMetricsPipelineBinder(Provider<MeterRegistry> meterRegistryProvider) {
        this.metricsHandler = new ChannelMetricsHandler(meterRegistryProvider);
    }

    @Override
    public ChannelPipelineCustomizer onCreated(BeanCreatedEvent<ChannelPipelineCustomizer> event) {
        final ChannelPipelineCustomizer customizer = event.getBean();
        if (customizer.isServerChannel()) {
            customizer.doOnConnect(pipeline -> {
                pipeline.addFirst(ChannelMetricsHandler.CHANNEL_METRICS, metricsHandler);
                return pipeline;
            });
        }
        return customizer;
    }

}
