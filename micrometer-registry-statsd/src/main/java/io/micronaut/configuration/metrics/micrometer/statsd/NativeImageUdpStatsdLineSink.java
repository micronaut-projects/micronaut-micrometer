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
package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.statsd.StatsdConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.UncheckedIOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Native-image friendly UDP line sink for StatsD.
 */
final class NativeImageUdpStatsdLineSink implements Consumer<String>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(NativeImageUdpStatsdLineSink.class);

    private final Object lock = new Object();
    private final String host;
    private final int port;
    private final int maxPacketLength;
    private final boolean buffered;
    private final ScheduledExecutorService scheduler;
    private final Consumer<String> sender;

    private StringBuilder buffer;

    NativeImageUdpStatsdLineSink(StatsdConfig config) {
        this(config, payload -> {
            try (DatagramSocket currentSocket = new DatagramSocket()) {
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                InetAddress address = InetAddress.getByName(config.host());
                currentSocket.send(new DatagramPacket(bytes, bytes.length, address, config.port()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    NativeImageUdpStatsdLineSink(StatsdConfig config, Consumer<String> sender) {
        this.host = config.host();
        this.port = config.port();
        this.maxPacketLength = config.maxPacketLength();
        this.buffered = config.buffered();
        this.sender = sender;
        this.buffer = new StringBuilder(Math.max(128, maxPacketLength));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new StatsdThreadFactory());
        Duration pollingFrequency = config.pollingFrequency();
        this.scheduler.scheduleAtFixedRate(this::flushSafely,
            pollingFrequency.toMillis(),
            pollingFrequency.toMillis(),
            TimeUnit.MILLISECONDS);
    }

    @Override
    public void accept(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (!buffered) {
                send(line);
                return;
            }
            int additionalLength = line.length();
            if (!buffer.isEmpty()) {
                additionalLength += 1;
            }
            if (buffer.length() + additionalLength > maxPacketLength && !buffer.isEmpty()) {
                send(buffer.toString());
                buffer = new StringBuilder(Math.max(128, maxPacketLength));
            }
            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(line);
        }
    }

    private void flushSafely() {
        synchronized (lock) {
            if (!buffer.isEmpty()) {
                send(buffer.toString());
                buffer = new StringBuilder(Math.max(128, maxPacketLength));
            }
        }
    }

    private void send(String payload) {
        try {
            sender.accept(payload);
        } catch (RuntimeException e) {
            LOG.debug("Error sending StatsD metrics to {}:{}", host, port, e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        synchronized (lock) {
            if (!buffer.isEmpty()) {
                send(buffer.toString());
                buffer = new StringBuilder(Math.max(128, maxPacketLength));
            }
        }
    }

    private static final class StatsdThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("micronaut-statsd-native-sink");
            thread.setDaemon(true);
            return thread;
        }
    }
}
