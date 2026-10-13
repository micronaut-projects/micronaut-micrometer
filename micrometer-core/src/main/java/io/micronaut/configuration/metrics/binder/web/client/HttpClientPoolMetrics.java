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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.netty.NettyClientCustomizer;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * The meters of the Netty HTTP client connection pools. The meters are the same for every client
 * and every per-host pool, so a pool that the client evicts leaves no meter behind: its
 * connections close, and the gauges count them down.
 *
 * @since 6.2.0
 */
@Singleton
@Internal
@RequiresMetrics
@Requires(classes = NettyClientCustomizer.class)
@Requires(property = HttpClientPoolMetrics.ENABLED, defaultValue = FALSE, notEquals = FALSE)
final class HttpClientPoolMetrics {
    static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".web.client.pool.enabled";

    static final String CONNECTIONS = "http.client.pool.connections";
    static final String CONNECTIONS_CREATED = "http.client.pool.connections.created";
    static final String CONNECTIONS_CREATE_TIME = "http.client.pool.connections.create.time";
    static final String CONNECTIONS_FAILED = "http.client.pool.connections.failed";
    static final String REQUESTS = "http.client.pool.requests";

    static final String STATE_TAG = "state";
    static final String STATE_OPEN = "open";
    static final String STATE_PENDING = "pending";
    static final String STATE_ACTIVE = "active";
    static final String PROTOCOL_TAG = "protocol";
    static final String HTTP_1_1 = "http/1.1";
    static final String HTTP_2 = "h2";
    static final String HTTP_3 = "h3";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger openConnections = new AtomicInteger();
    private final AtomicInteger pendingConnections = new AtomicInteger();
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final Counter failedConnections;
    private final Map<String, Counter> createdConnections;
    private final Map<String, Timer> connectionCreateTimes;

    HttpClientPoolMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder(CONNECTIONS, openConnections, AtomicInteger::get)
            .tag(STATE_TAG, STATE_OPEN)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The number of HTTP client pool connections that are open.")
            .register(meterRegistry);
        Gauge.builder(CONNECTIONS, pendingConnections, AtomicInteger::get)
            .tag(STATE_TAG, STATE_PENDING)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The number of HTTP client pool connections that are being established.")
            .register(meterRegistry);
        Gauge.builder(REQUESTS, activeRequests, AtomicInteger::get)
            .tag(STATE_TAG, STATE_ACTIVE)
            .description("The number of HTTP client requests that run on a pool connection.")
            .register(meterRegistry);
        failedConnections = Counter.builder(CONNECTIONS_FAILED)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The number of HTTP client pool connections that closed before they were established.")
            .register(meterRegistry);
        List<String> protocols = List.of(HTTP_1_1, HTTP_2, HTTP_3);
        createdConnections = protocols.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), protocol ->
            Counter.builder(CONNECTIONS_CREATED)
                .tag(PROTOCOL_TAG, protocol)
                .baseUnit(BaseUnits.CONNECTIONS)
                .description("The number of HTTP client pool connections that were established.")
                .register(meterRegistry)));
        connectionCreateTimes = protocols.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), protocol ->
            Timer.builder(CONNECTIONS_CREATE_TIME)
                .tag(PROTOCOL_TAG, protocol)
                .description("The time from the start of a connection attempt until the HTTP client pool can use the connection.")
                .register(meterRegistry)));
    }

    /**
     * Start tracking a connection attempt.
     *
     * @return The attempt
     */
    ConnectionAttempt beginConnectionAttempt() {
        pendingConnections.incrementAndGet();
        return new ConnectionAttempt(Timer.start(meterRegistry));
    }

    void requestStarted() {
        activeRequests.incrementAndGet();
    }

    void requestFinished() {
        activeRequests.decrementAndGet();
    }

    /**
     * A connection attempt, from the start of the connect until the connection closes.
     */
    final class ConnectionAttempt {
        private static final int PENDING = 0;
        private static final int OPEN = 1;
        private static final int DONE = 2;

        private final AtomicInteger state = new AtomicInteger(PENDING);
        private final Timer.Sample sample;

        private ConnectionAttempt(Timer.Sample sample) {
            this.sample = sample;
        }

        /**
         * The connection can carry requests of the pool.
         *
         * @param protocol The protocol of the connection
         */
        void established(String protocol) {
            if (state.compareAndSet(PENDING, OPEN)) {
                pendingConnections.decrementAndGet();
                openConnections.incrementAndGet();
                createdConnections.get(protocol).increment();
                sample.stop(connectionCreateTimes.get(protocol));
            }
        }

        /**
         * The connection is not a pool connection, for example a WebSocket connection.
         */
        void abandoned() {
            if (state.compareAndSet(PENDING, DONE)) {
                pendingConnections.decrementAndGet();
            }
        }

        /**
         * The channel of the connection closed. Called once for each channel of the connection.
         */
        void closed() {
            if (state.compareAndSet(PENDING, DONE)) {
                pendingConnections.decrementAndGet();
                failedConnections.increment();
            } else if (state.compareAndSet(OPEN, DONE)) {
                openConnections.decrementAndGet();
            }
        }
    }
}
