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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records HTTP client pool metrics.
 */
@Singleton
@Internal
final class HttpClientPoolMetricsRecorder {
    static final String CONNECTIONS = "http.client.pool.connections";
    static final String REQUESTS = "http.client.pool.requests";
    static final String CONNECTIONS_CREATED = "http.client.pool.connections.created";
    static final String CONNECTIONS_CREATE_TIME = "http.client.pool.connections.create.time";
    static final String CLIENT_TAG = "client";
    static final String STATE_TAG = "state";
    static final String CLIENT_ALL = "all";
    static final String STATE_OPEN = "open";
    static final String STATE_PENDING = "pending";
    static final String STATE_ACTIVE = "active";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger openConnectionCount = new AtomicInteger();
    private final AtomicInteger pendingConnectionCount = new AtomicInteger();
    private final Counter createdConnectionCount;
    private final Timer connectionCreateTimer;

    HttpClientPoolMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Tags openTags = Tags.of(CLIENT_TAG, CLIENT_ALL, STATE_TAG, STATE_OPEN);
        Tags pendingTags = Tags.of(CLIENT_TAG, CLIENT_ALL, STATE_TAG, STATE_PENDING);
        Gauge.builder(CONNECTIONS, openConnectionCount, AtomicInteger::get)
            .tags(openTags)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The number of HTTP client pool connections currently open.")
            .register(meterRegistry);
        Gauge.builder(CONNECTIONS, pendingConnectionCount, AtomicInteger::get)
            .tags(pendingTags)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The number of HTTP client pool connections currently being established.")
            .register(meterRegistry);
        createdConnectionCount = Counter.builder(CONNECTIONS_CREATED)
            .tag(CLIENT_TAG, CLIENT_ALL)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The number of HTTP client pool connections created.")
            .register(meterRegistry);
        connectionCreateTimer = Timer.builder(CONNECTIONS_CREATE_TIME)
            .tag(CLIENT_TAG, CLIENT_ALL)
            .description("The time taken to create HTTP client pool connections.")
            .register(meterRegistry);
    }

    ConnectionAttempt beginConnectionAttempt() {
        pendingConnectionCount.incrementAndGet();
        return new ConnectionAttempt(Timer.start(meterRegistry));
    }

    void connectionEstablished(ConnectionAttempt attempt) {
        if (attempt.pending.compareAndSet(true, false)) {
            pendingConnectionCount.decrementAndGet();
            openConnectionCount.incrementAndGet();
            attempt.open.set(true);
            createdConnectionCount.increment();
            attempt.sample.stop(connectionCreateTimer);
        }
    }

    void connectionClosed(ConnectionAttempt attempt) {
        if (attempt.pending.compareAndSet(true, false)) {
            pendingConnectionCount.decrementAndGet();
        }
        if (attempt.open.compareAndSet(true, false)) {
            openConnectionCount.decrementAndGet();
        }
    }

    MeterRegistry meterRegistry() {
        return meterRegistry;
    }

    static final class ConnectionAttempt {
        private final Timer.Sample sample;
        private final AtomicBoolean pending = new AtomicBoolean(true);
        private final AtomicBoolean open = new AtomicBoolean(false);

        private ConnectionAttempt(Timer.Sample sample) {
            this.sample = sample;
        }
    }
}
