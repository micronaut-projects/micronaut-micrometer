package io.micronaut.configuration.metrics.micrometer.prometheus.management

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

import java.io.IOException
import java.io.OutputStream
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService

class PrometheusEndpointSpec extends Specification {

    def setupSpec() {
        def registry = embeddedServer.applicationContext.getBean(MeterRegistry)

        new JvmMemoryMetrics().bindTo(registry)
        new ProcessorMetrics().bindTo(registry)
    }

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'endpoints.prometheus.sensitive'                  : false,
            'micronaut.metrics.export.prometheus.descriptions': false
    ])

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    void "test prometheus scrape"() {
        expect:
        client.toBlocking().retrieve('/prometheus').contains('jvm_memory_used')
    }

    void "test prometheus scrape runs off the event loop"() {
        given:
        def registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        String scrapeThread = null
        Gauge.builder('test.scrape.thread', { ->
            scrapeThread = Thread.currentThread().toString()
            1d
        }).register(registry)

        when:
        client.toBlocking().retrieve('/prometheus')

        then:
        scrapeThread != null
        !scrapeThread.toLowerCase(Locale.ROOT).contains('eventloop')
    }

    void "test large prometheus scrape with a thread per task blocking executor"() {
        given: 'a scrape larger than the 64KB pipe buffer the endpoint used to stream through'
        def server = ApplicationContext.run(EmbeddedServer, [
                'endpoints.prometheus.sensitive'                  : false,
                'micronaut.metrics.export.prometheus.descriptions': false,
                'micronaut.executors.blocking.type'               : 'thread_per_task',
                'micronaut.executors.blocking.virtual'            : true
        ])
        def httpClient = server.applicationContext.createBean(HttpClient, server.URL)
        def registry = server.applicationContext.getBean(MeterRegistry)
        int meterCount = 2000
        meterCount.times { i ->
            Counter.builder("test.large.scrape.meter.with.a.long.name.$i")
                    .tag('index', String.valueOf(i))
                    .register(registry)
                    .increment()
        }

        when:
        List<String> bodies = (1..50).collect { httpClient.toBlocking().retrieve('/prometheus') }

        then:
        bodies.every { body ->
            body.getBytes(StandardCharsets.UTF_8).length > 64 * 1024 &&
                    body.readLines().count { it.startsWith('test_large_scrape_meter_with_a_long_name_') } == meterCount &&
                    body.contains("test_large_scrape_meter_with_a_long_name_${meterCount - 1}_total{index=\"${meterCount - 1}\"} 1.0")
        }

        cleanup:
        httpClient?.close()
        server?.close()
    }

    void "test large prometheus scrape read by short lived threads with a slow consumer"() {
        given: 'a scrape larger than 64KB, read in chunks by threads that terminate between reads'
        def registry = new PrometheusMeterRegistry(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT)
        2000.times { i ->
            Counter.builder("test.large.scrape.meter.with.a.long.name.$i").register(registry).increment()
        }
        def endpoint = new PrometheusEndpoint(registry)
        def expected = registry.scrape()

        when:
        def inputStream = endpoint.scrapeStream()
        def output = new ByteArrayOutputStream()
        IOException failure = null
        int chunks = 0
        boolean done = false
        while (!done && failure == null) {
            Thread.ofVirtual().start {
                try {
                    byte[] chunk = new byte[8192]
                    int read = inputStream.read(chunk)
                    if (read == -1) {
                        done = true
                    } else {
                        output.write(chunk, 0, read)
                    }
                } catch (IOException e) {
                    failure = e
                }
            }.join()
            if (++chunks == 1) {
                // a stalled consumer: the reading thread is gone while the scrape is still being produced
                Thread.sleep(1500)
            }
        }
        inputStream.close()

        then:
        expected.getBytes(StandardCharsets.UTF_8).length > 64 * 1024
        failure == null
        output.toString(StandardCharsets.UTF_8) == expected

        cleanup:
        registry.close()
    }

    void "test prometheus scrape streams output"() {
        given:
        def registry = Mock(PrometheusMeterRegistry)
        def endpoint = new PrometheusEndpoint(registry)

        when:
        String result
        endpoint.scrapeStream().withCloseable { inputStream ->
            result = inputStream.getText(StandardCharsets.UTF_8.name())
        }

        then:
        result == 'jvm_memory_used 1.0\n'
        1 * registry.scrape(_ as OutputStream, PrometheusEndpoint.TEXT_FORMAT, null) >> { OutputStream outputStream, String contentType, Set<String> includedNames ->
            outputStream.write('jvm_memory_used 1.0\n'.getBytes(StandardCharsets.UTF_8))
        }
        0 * registry.scrape()
    }

    void "test prometheus scrape retains direct string access"() {
        given:
        def registry = Mock(PrometheusMeterRegistry)
        def endpoint = new PrometheusEndpoint(registry)

        when:
        String result = endpoint.scrape()

        then:
        result == 'jvm_memory_used 1.0\n'
        1 * registry.scrape() >> 'jvm_memory_used 1.0\n'
        0 * registry.scrape(_ as OutputStream)
    }

    void "test prometheus scrape stream propagates io failure"() {
        given:
        def registry = Mock(PrometheusMeterRegistry)
        def endpoint = new PrometheusEndpoint(registry)
        def failure = new IOException('boom')

        when:
        endpoint.scrapeStream()

        then:
        def exception = thrown(UncheckedIOException)
        exception.cause.is(failure)
        1 * registry.scrape(_ as OutputStream, PrometheusEndpoint.TEXT_FORMAT, null) >> { OutputStream outputStream, String contentType, Set<String> includedNames ->
            throw failure
        }
        0 * registry.scrape()
    }

    void "test prometheus scrape stream supports single byte reads"() {
        given:
        def registry = Mock(PrometheusMeterRegistry)
        def endpoint = new PrometheusEndpoint(registry)

        when:
        def bytes = []
        endpoint.scrapeStream().withCloseable { inputStream ->
            int nextByte
            while ((nextByte = inputStream.read()) != -1) {
                bytes << nextByte
            }
        }

        then:
        new String((byte[]) bytes, StandardCharsets.UTF_8) == 'abc'
        1 * registry.scrape(_ as OutputStream, PrometheusEndpoint.TEXT_FORMAT, null) >> { OutputStream outputStream, String contentType, Set<String> includedNames ->
            outputStream.write('abc'.getBytes(StandardCharsets.UTF_8))
        }
        0 * registry.scrape()
    }

    void "test prometheus scrape stream does not use the deprecated executor"() {
        given:
        def registry = Mock(PrometheusMeterRegistry)
        def executor = Mock(ExecutorService)
        def endpoint = new PrometheusEndpoint(registry, executor)

        when:
        String result = endpoint.scrapeStream().getText(StandardCharsets.UTF_8.name())

        then:
        result == 'abc'
        1 * registry.scrape(_ as OutputStream, PrometheusEndpoint.TEXT_FORMAT, null) >> { OutputStream outputStream, String contentType, Set<String> includedNames ->
            outputStream.write('abc'.getBytes(StandardCharsets.UTF_8))
        }
        0 * executor._
    }

    void "test prometheus scrape buffer is sized from the previous scrape"(int previousSize, int expected) {
        expect:
        PrometheusEndpoint.initialBufferSize(previousSize) == expected

        where:
        previousSize      | expected
        0                 | 8 * 1024
        1024              | 8 * 1024
        64 * 1024         | 72 * 1024
        1024 * 1024       | 1152 * 1024
        Integer.MAX_VALUE | Integer.MAX_VALUE - 8
    }

    @PendingFeature
    void "test prometheus scrape no descriptions"() {
        given:
        def result = client.toBlocking().retrieve('/prometheus')

        expect:
        result.contains('jvm_memory_used')
        !result.contains('# TYPE')
    }
}
