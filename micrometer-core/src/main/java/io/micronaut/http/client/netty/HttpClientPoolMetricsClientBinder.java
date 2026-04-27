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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.CLIENT_TAG;
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.CONNECTIONS;
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.REQUESTS;
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.STATE_ACTIVE;
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.STATE_OPEN;
import static io.micronaut.http.client.netty.HttpClientPoolMetricsRecorder.STATE_TAG;

/**
 * Adds per-client gauges for default Netty HTTP client pool state.
 */
@Singleton
@Internal
@RequiresMetrics
@Requires(classes = DefaultHttpClient.class)
@Requires(property = MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled", value = "true")
final class HttpClientPoolMetricsClientBinder implements BeanCreatedEventListener<DefaultHttpClient> {
    private final HttpClientPoolMetricsRecorder recorder;

    HttpClientPoolMetricsClientBinder(HttpClientPoolMetricsRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public DefaultHttpClient onCreated(BeanCreatedEvent<DefaultHttpClient> event) {
        DefaultHttpClient client = event.getBean();
        String clientName = event.getBeanIdentifier().getName();
        Tags openTags = Tags.of(CLIENT_TAG, clientName, STATE_TAG, STATE_OPEN);
        Tags activeTags = Tags.of(CLIENT_TAG, clientName, STATE_TAG, STATE_ACTIVE);
        Gauge.builder(CONNECTIONS, client, c -> c.connectionManager().getChannels().size())
            .tags(openTags)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The number of HTTP client pool connections currently open for this client.")
            .register(recorder.meterRegistry());
        Gauge.builder(REQUESTS, client, c -> c.connectionManager().liveRequestCount())
            .tags(activeTags)
            .description("The number of HTTP client requests currently checked out from the pool for this client.")
            .register(recorder.meterRegistry());
        return client;
    }
}
