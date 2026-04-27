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
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private int bufferBytes;

    NativeImageUdpStatsdLineSink(StatsdConfig config) {
        this(config, new UdpSender(config.host(), config.port()));
    }

    NativeImageUdpStatsdLineSink(StatsdConfig config, Consumer<String> sender) {
        this.host = config.host();
        this.port = config.port();
        this.maxPacketLength = config.maxPacketLength();
        this.buffered = config.buffered();
        this.sender = sender;
        this.buffer = new StringBuilder(Math.max(128, maxPacketLength));
        Duration pollingFrequency = config.pollingFrequency();
        long pollingFrequencyMillis = pollingFrequency.toMillis();
        if (buffered && pollingFrequencyMillis > 0) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(new StatsdThreadFactory());
            this.scheduler.scheduleAtFixedRate(this::flushSafely,
                pollingFrequencyMillis,
                pollingFrequencyMillis,
                TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    @Override
    public void accept(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        String payloadToSend = null;
        synchronized (lock) {
            if (!buffered) {
                payloadToSend = line;
            } else {
                int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
                int additionalLength = lineBytes;
                if (!buffer.isEmpty()) {
                    additionalLength += 1;
                }
                if (bufferBytes + additionalLength > maxPacketLength && !buffer.isEmpty()) {
                    payloadToSend = clearBuffer();
                }
                if (!buffer.isEmpty()) {
                    buffer.append('\n');
                    bufferBytes++;
                }
                buffer.append(line);
                bufferBytes += lineBytes;
            }
        }
        if (payloadToSend != null) {
            send(payloadToSend);
        }
    }

    private void flushSafely() {
        String payloadToSend;
        synchronized (lock) {
            payloadToSend = clearBuffer();
        }
        if (payloadToSend != null) {
            send(payloadToSend);
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
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        String payloadToSend;
        synchronized (lock) {
            payloadToSend = clearBuffer();
        }
        if (payloadToSend != null) {
            send(payloadToSend);
        }
        closeSender();
    }

    private String clearBuffer() {
        if (buffer.isEmpty()) {
            return null;
        }
        String payload = buffer.toString();
        buffer = new StringBuilder(Math.max(128, maxPacketLength));
        bufferBytes = 0;
        return payload;
    }

    private void closeSender() {
        if (sender instanceof Closeable closeableSender) {
            try {
                closeableSender.close();
            } catch (IOException e) {
                LOG.debug("Error closing StatsD sender to {}:{}", host, port, e);
            }
        }
    }

    private static final class UdpSender implements Consumer<String>, Closeable {
        private final Object lock = new Object();
        private final String host;
        private final int port;

        private DatagramSocket socket;
        private InetAddress address;

        private UdpSender(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void accept(String payload) {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            synchronized (lock) {
                try {
                    DatagramSocket currentSocket = getSocket();
                    InetAddress currentAddress = getAddress();
                    currentSocket.send(new DatagramPacket(bytes, bytes.length, currentAddress, port));
                } catch (IOException e) {
                    closeSocket();
                    address = null;
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                closeSocket();
                address = null;
            }
        }

        private DatagramSocket getSocket() throws IOException {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
            }
            return socket;
        }

        private InetAddress getAddress() throws IOException {
            if (address == null) {
                address = InetAddress.getByName(host);
            }
            return address;
        }

        private void closeSocket() {
            if (socket != null) {
                socket.close();
                socket = null;
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
