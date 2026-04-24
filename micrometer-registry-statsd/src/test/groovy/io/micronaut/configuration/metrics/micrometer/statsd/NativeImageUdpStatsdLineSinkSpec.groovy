package io.micronaut.configuration.metrics.micrometer.statsd

import io.micrometer.statsd.StatsdConfig
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class NativeImageUdpStatsdLineSinkSpec extends Specification {

    private static final String IMAGE_CODE_PROPERTY = NativeImageStatsdCondition.NATIVE_IMAGE_CODE_PROPERTY

    @Unroll
    void "verify native image line sink bean presence = #present for imageCode=#imageCode protocol=#protocol"() {
        given:
        if (imageCode != null) {
            System.setProperty(IMAGE_CODE_PROPERTY, imageCode)
        } else {
            System.clearProperty(IMAGE_CODE_PROPERTY)
        }
        ApplicationContext context = ApplicationContext.run([
            (StatsdMeterRegistryFactory.STATSD_ENABLED): true,
            (StatsdMeterRegistryFactory.STATSD_CONFIG + ".protocol"): protocol
        ])

        expect:
        context.findBean(NativeImageUdpStatsdLineSink).present == present

        cleanup:
        context.close()
        System.clearProperty(IMAGE_CODE_PROPERTY)

        where:
        imageCode | protocol | present
        "runtime" | "udp"    | true
        "runtime" | "tcp"    | false
        null      | "udp"    | false
    }

    void "verify native image line sink sends buffered payloads over udp"() {
        given:
        List<String> payloads = []
        NativeImageUdpStatsdLineSink sink = new NativeImageUdpStatsdLineSink(config(
            host: "127.0.0.1",
            port: "8125",
            buffered: "true",
            pollingFrequency: "PT10S",
            maxPacketLength: "256"
        ), payloads.&add)

        when:
        sink.accept("first:1|c")
        sink.accept("second:2|c")
        sink.close()

        then:
        payloads == ["first:1|c\nsecond:2|c"]

        cleanup:
        sink?.close()
    }

    void "verify native image line sink does not hold lock while sending"() {
        given:
        CountDownLatch firstSendStarted = new CountDownLatch(1)
        CountDownLatch allowFirstSendToFinish = new CountDownLatch(1)
        CountDownLatch secondSendStarted = new CountDownLatch(1)
        AtomicInteger sendCount = new AtomicInteger()
        Thread first = null
        Thread second = null
        NativeImageUdpStatsdLineSink sink = new NativeImageUdpStatsdLineSink(config(
            host: "127.0.0.1",
            port: "8125",
            buffered: "false",
            pollingFrequency: "PT10S",
            maxPacketLength: "256"
        ), { String payload ->
            int currentSend = sendCount.incrementAndGet()
            if (currentSend == 1) {
                firstSendStarted.countDown()
                allowFirstSendToFinish.await(5, TimeUnit.SECONDS)
            } else if (currentSend == 2) {
                secondSendStarted.countDown()
            }
        })
        first = Thread.start {
            sink.accept("first:1|c")
        }

        expect:
        firstSendStarted.await(5, TimeUnit.SECONDS)

        when:
        second = Thread.start {
            sink.accept("second:2|c")
        }

        then:
        secondSendStarted.await(5, TimeUnit.SECONDS)

        cleanup:
        allowFirstSendToFinish?.countDown()
        first?.join(5_000)
        second?.join(5_000)
        sink?.close()
    }

    void "verify native image line sink keeps sending after listener starts later"() {
        given:
        DatagramSocket reservation = new DatagramSocket(0)
        int port = reservation.localPort
        reservation.close()
        NativeImageUdpStatsdLineSink sink = new NativeImageUdpStatsdLineSink(config(
            host: "127.0.0.1",
            port: port.toString(),
            buffered: "false",
            pollingFrequency: "PT0.05S",
            maxPacketLength: "256"
        ))

        when:
        sink.accept("dropped:1|c")
        DatagramSocket server = new DatagramSocket(port)
        server.soTimeout = 5_000
        sink.accept("recovered:2|c")
        String packet = receive(server)

        then:
        packet == "recovered:2|c"

        cleanup:
        sink?.close()
        server?.close()
    }

    void "verify native image line sink bean absent when metrics are disabled"() {
        given:
        System.setProperty(IMAGE_CODE_PROPERTY, "runtime")
        ApplicationContext context = ApplicationContext.run([
            (MICRONAUT_METRICS_ENABLED)               : false,
            (StatsdMeterRegistryFactory.STATSD_ENABLED): true,
            (StatsdMeterRegistryFactory.STATSD_CONFIG + ".protocol"): "udp"
        ])

        expect:
        !context.findBean(NativeImageUdpStatsdLineSink).present

        cleanup:
        context.close()
        System.clearProperty(IMAGE_CODE_PROPERTY)
    }

    private static StatsdConfig config(Map<String, String> properties) {
        Map<String, String> prefixedProperties = properties.collectEntries { String key, String value ->
            [((key.startsWith('statsd.') ? key : "statsd.${key}").toString()): value]
        }
        return { String key -> properties.get(key) ?: prefixedProperties.get(key) } as StatsdConfig
    }

    private static String receive(DatagramSocket socket) {
        byte[] buffer = new byte[512]
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length)
        socket.receive(packet)
        return new String(packet.data, packet.offset, packet.length)
    }
}
