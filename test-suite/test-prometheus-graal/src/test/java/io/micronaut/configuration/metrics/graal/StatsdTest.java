package io.micronaut.configuration.metrics.graal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the StatsD registry sends metrics over UDP, which in a native image uses the
 * native image line sink instead of the shaded Netty client.
 */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatsdTest implements TestPropertyProvider {

    private final DatagramSocket socket = createSocket();

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    StatsdMeterRegistry statsdMeterRegistry;

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
            "micronaut.metrics.export.statsd.enabled", "true",
            "micronaut.metrics.export.statsd.host", "127.0.0.1",
            "micronaut.metrics.export.statsd.port", String.valueOf(socket.getLocalPort()),
            "micronaut.metrics.export.statsd.buffered", "false"
        );
    }

    @AfterAll
    void closeSocket() {
        socket.close();
    }

    @Test
    void testStatsdSendsMetrics() throws IOException {
        Counter counter = Counter.builder("native.statsd.counter").register(meterRegistry);

        byte[] buffer = new byte[65535];
        long deadline = System.nanoTime() + 30_000_000_000L;
        boolean received = false;
        while (!received && System.nanoTime() < deadline) {
            counter.increment();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                continue;
            }
            String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
            received = payload.contains("native.statsd.counter");
        }

        assertTrue(received, "No StatsD line received for native.statsd.counter");
    }

    private static DatagramSocket createSocket() {
        try {
            DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
            socket.setSoTimeout(1000);
            return socket;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
