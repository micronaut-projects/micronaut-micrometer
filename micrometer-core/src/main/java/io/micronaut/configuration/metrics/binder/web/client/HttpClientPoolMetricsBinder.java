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

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.netty.NettyClientCustomizer;
import jakarta.inject.Singleton;

import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Registers the {@link HttpClientPoolMetricsCustomizer} with the Netty HTTP clients.
 *
 * @since 6.2.0
 */
@Singleton
@Internal
@RequiresMetrics
@Requires(classes = NettyClientCustomizer.Registry.class)
@Requires(property = HttpClientPoolMetrics.ENABLED, defaultValue = FALSE, notEquals = FALSE)
final class HttpClientPoolMetricsBinder implements BeanCreatedEventListener<NettyClientCustomizer.Registry> {
    private final HttpClientPoolMetrics metrics;

    HttpClientPoolMetricsBinder(HttpClientPoolMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public NettyClientCustomizer.Registry onCreated(BeanCreatedEvent<NettyClientCustomizer.Registry> event) {
        NettyClientCustomizer.Registry registry = event.getBean();
        registry.register(new HttpClientPoolMetricsCustomizer(metrics));
        return registry;
    }
}
