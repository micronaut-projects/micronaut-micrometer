package io.micronaut.configuration.metrics.micrometer.statsd

import io.micronaut.core.annotation.TypeHint
import spock.lang.Specification

class StatsdNativeImageMetadataSpec extends Specification {

    void "verify shaded native-image properties are packaged for statsd"() {
        given:
        String path = 'META-INF/native-image/io.micronaut.micrometer/micronaut-micrometer-registry-statsd/native-image.properties'

        when:
        InputStream stream = StatsdMeterRegistryFactory.classLoader.getResourceAsStream(path)

        then:
        stream != null

        when:
        String contents = stream.getText('UTF-8')

        then:
        contents.contains('io.micrometer.shaded.io.netty.buffer.PooledByteBufAllocator')
        contents.contains('io.micrometer.shaded.io.netty.resolver.dns.DnsNameResolver')
        contents.contains('io.micrometer.shaded.io.netty.channel.epoll')
        !contents.contains('--initialize-at-run-time=io.netty.')

        cleanup:
        stream?.close()
    }

    void "verify statsd factory declares shaded netty reflection hints"() {
        when:
        TypeHint hint = StatsdMeterRegistryFactory.getAnnotation(TypeHint)

        then:
        hint != null
        hint.typeNames().contains('io.micrometer.shaded.io.netty.buffer.AbstractByteBufAllocator')
        hint.typeNames().contains('io.micrometer.shaded.io.netty.util.ReferenceCountUtil')
        hint.typeNames().contains('io.micrometer.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerLimitField')
    }
}
