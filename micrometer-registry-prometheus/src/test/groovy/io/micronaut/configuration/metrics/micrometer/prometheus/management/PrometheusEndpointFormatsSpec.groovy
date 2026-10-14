package io.micronaut.configuration.metrics.micrometer.prometheus.management

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PrometheusEndpointFormatsSpec extends Specification {

    // the Accept header that Prometheus sends by default
    static final String PROMETHEUS_ACCEPT = 'application/openmetrics-text;version=1.0.0,application/openmetrics-text;version=0.0.1;q=0.75,text/plain;version=0.0.4;q=0.5,*/*;q=0.1'
    static final String PROMETHEUS_3_ACCEPT = 'application/openmetrics-text;version=1.0.0;escaping=allow-utf-8;q=0.6,application/openmetrics-text;version=0.0.1;q=0.5,text/plain;version=1.0.0;escaping=allow-utf-8;q=0.4,text/plain;version=0.0.4;q=0.3,*/*;q=0.2'
    static final String PROTOBUF_ACCEPT = 'application/vnd.google.protobuf;proto=io.prometheus.client.MetricFamily;encoding=delimited;q=0.7,text/plain;version=0.0.4;q=0.3'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'endpoints.prometheus.sensitive': false
    ])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    def setupSpec() {
        Counter.builder('test.formats.counter').register(server.applicationContext.getBean(MeterRegistry)).increment()
    }

    void "the Prometheus text format is returned by default"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/prometheus'), String)

        then:
        response.header(HttpHeaders.CONTENT_TYPE) == PrometheusEndpoint.TEXT_FORMAT
        response.body().contains('test_formats_counter_total 1.0')
        !response.body().contains('# EOF')
    }

    void "the Prometheus text format is returned when it is asked for"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, 'text/plain'), String)

        then:
        response.header(HttpHeaders.CONTENT_TYPE) == PrometheusEndpoint.TEXT_FORMAT
        response.body().contains('test_formats_counter_total 1.0')
    }

    void "the OpenMetrics format is returned for #accept"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, accept), String)

        then:
        response.header(HttpHeaders.CONTENT_TYPE) == PrometheusEndpoint.OPENMETRICS_FORMAT
        response.body().contains('test_formats_counter_total 1.0')
        response.body().endsWith('# EOF\n')

        where:
        accept << ['application/openmetrics-text', PROMETHEUS_ACCEPT]
    }

    void "the quality of the accepted formats selects #expected for #accept"() {
        when:
        HttpResponse<byte[]> response = client.toBlocking().exchange(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, accept), byte[])

        then:
        response.header(HttpHeaders.CONTENT_TYPE) == expected

        where:
        accept                                                                                                      | expected
        // a format with q=0 is not acceptable
        'text/plain;q=1, application/openmetrics-text;q=0'                                                          | PrometheusEndpoint.TEXT_FORMAT
        'application/openmetrics-text;q=0, text/plain'                                                              | PrometheusEndpoint.TEXT_FORMAT
        'application/vnd.google.protobuf;proto=io.prometheus.client.MetricFamily;encoding=delimited;q=0, */*;q=0.1' | PrometheusEndpoint.TEXT_FORMAT
        // the format with the highest quality wins, whatever its position in the header
        'text/plain;q=1, application/openmetrics-text;q=0.1'                                                        | PrometheusEndpoint.TEXT_FORMAT
        'text/plain;q=0.2, application/openmetrics-text;q=0.9'                                                      | PrometheusEndpoint.OPENMETRICS_FORMAT
        'application/openmetrics-text;q=1, text/plain;q=0.5'                                                        | PrometheusEndpoint.OPENMETRICS_FORMAT
        'text/plain;q=0.3, application/vnd.google.protobuf;proto=io.prometheus.client.MetricFamily;encoding=delimited;q=0.7' | PrometheusEndpoint.PROTOBUF_FORMAT
        // the quality of a format is that of the most specific range that matches it
        'text/plain;q=0, */*;q=1'                                                                                   | PrometheusEndpoint.OPENMETRICS_FORMAT
        'text/*;q=1, text/plain;q=0.1, application/openmetrics-text;q=0.8'                                          | PrometheusEndpoint.OPENMETRICS_FORMAT
        'text/plain;version=0.0.4;q=0.2, text/plain;q=0.9, application/openmetrics-text;q=0.5'                      | PrometheusEndpoint.OPENMETRICS_FORMAT
        'text/*;q=0.5, application/openmetrics-text;q=0.2'                                                          | PrometheusEndpoint.TEXT_FORMAT
        'application/*;q=0.9, application/openmetrics-text;q=0, text/plain;q=0.5'                                   | PrometheusEndpoint.PROTOBUF_FORMAT
        '*/*'                                                                                                       | PrometheusEndpoint.TEXT_FORMAT
        // the parameters of a range must match those of the representation
        'text/plain;charset=utf-16;q=1, application/openmetrics-text;q=0.5'                                        | PrometheusEndpoint.OPENMETRICS_FORMAT
        'text/plain;charset=UTF-8;q=1, application/openmetrics-text;q=0.5'                                         | PrometheusEndpoint.TEXT_FORMAT
        // the name of the quality parameter is case-insensitive
        'application/openmetrics-text;Q=0, text/plain;q=0.5'                                                       | PrometheusEndpoint.TEXT_FORMAT
        'application/openmetrics-text;Q=0.1, Text/Plain;q=0.5'                                                     | PrometheusEndpoint.TEXT_FORMAT
        // the Accept header of Prometheus 3: the escaping parameter does not select a representation
        PROMETHEUS_3_ACCEPT                                                                                         | PrometheusEndpoint.OPENMETRICS_FORMAT
    }

    void "the protobuf format is returned when it is asked for"() {
        when:
        HttpResponse<byte[]> response = client.toBlocking().exchange(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, PROTOBUF_ACCEPT), byte[])
        String body = new String(response.body(), StandardCharsets.UTF_8)

        then:
        response.header(HttpHeaders.CONTENT_TYPE) == PrometheusEndpoint.PROTOBUF_FORMAT
        body.contains('test_formats_counter')
        !body.contains('# TYPE')
    }

    void "the scrape is filtered with includedNames"() {
        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/prometheus?$query"), String)

        then:
        body.contains('test_formats_counter_total 1.0')
        body.contains('jvm_memory_used_bytes') == includesJvm
        !body.contains('process_cpu_usage')

        where:
        query                                                              | includesJvm
        'includedNames=test_formats_counter'                         | false
        'includedNames=test_formats_counter,jvm_memory_used_bytes'   | true
        'includedNames=test_formats_counter&includedNames=jvm_memory_used_bytes' | true
    }

    void "the Prometheus text format is returned when OpenMetrics is disabled"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'endpoints.prometheus.sensitive'                                : false,
                (PrometheusEndpoint.OPENMETRICS_ENABLED)                        : false
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        HttpResponse<String> openMetrics = client.toBlocking().exchange(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, PROMETHEUS_ACCEPT), String)
        HttpResponse<byte[]> protobuf = client.toBlocking().exchange(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, PROTOBUF_ACCEPT), byte[])

        then:
        openMetrics.header(HttpHeaders.CONTENT_TYPE) == PrometheusEndpoint.TEXT_FORMAT
        !openMetrics.body().contains('# EOF')
        protobuf.header(HttpHeaders.CONTENT_TYPE) == PrometheusEndpoint.PROTOBUF_FORMAT

        cleanup:
        client.close()
        server.close()
    }

    void "each OpenMetrics version is negotiated on its own when OpenMetrics 2 content negotiation is enabled"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'endpoints.prometheus.sensitive'                                                         : false,
                'micronaut.metrics.export.prometheus.properties.io.prometheus.openmetrics2.enabled'            : true,
                'micronaut.metrics.export.prometheus.properties.io.prometheus.openmetrics2.content_negotiation': true
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, accept), String)

        then:
        response.header(HttpHeaders.CONTENT_TYPE) == expected

        cleanup:
        client.close()
        server.close()

        where:
        accept                                                                                                           | expected
        'application/openmetrics-text;version=1.0.0;q=0.1, application/openmetrics-text;version=2.0.0;q=0.9, text/plain;q=0.5' | 'application/openmetrics-text; version=2.0.0; charset=utf-8'
        'application/openmetrics-text;version=2.0.0;q=0.1, application/openmetrics-text;version=1.0.0;q=0.9, text/plain;q=0.5' | PrometheusEndpoint.OPENMETRICS_FORMAT
        'application/openmetrics-text;version=2.0.0;q=0, application/openmetrics-text;q=0.5'                             | PrometheusEndpoint.OPENMETRICS_FORMAT
    }

    void "Prometheus client properties are passed through with their keys unchanged"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'endpoints.prometheus.sensitive'                                                      : false,
                'micronaut.metrics.export.prometheus.properties.io.prometheus.exporter.include_created_timestamps': true,
                'micronaut.metrics.export.prometheus.properties.io.prometheus.exemplars.sampleIntervalMilliseconds': 120
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)
        Counter.builder('test.created.counter').register(server.applicationContext.getBean(MeterRegistry)).increment()
        Properties properties = server.applicationContext.getBean(PrometheusConfig).prometheusProperties()

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET('/prometheus').header(HttpHeaders.ACCEPT, 'application/openmetrics-text'), String)

        then:
        properties.getProperty('io.prometheus.exporter.include_created_timestamps') == 'true'
        properties.getProperty('io.prometheus.exemplars.sampleIntervalMilliseconds') == '120'
        // the default from Micrometer is kept
        properties.getProperty('io.prometheus.exporter.exemplarsOnAllMetricTypes') == 'true'
        body.contains('test_created_counter_created')

        cleanup:
        client.close()
        server.close()
    }

    void "the scrape is buffered and sized from the previous scrape"() {
        given:
        PrometheusEndpoint endpoint = server.applicationContext.getBean(PrometheusEndpoint)

        when:
        HttpResponse<InputStream> first = endpoint.scrape('application/openmetrics-text', null)
        byte[] firstBytes = first.body().bytes
        HttpResponse<InputStream> second = endpoint.scrape('application/openmetrics-text', null)

        then:
        first.contentType.get().toString().startsWith('application/openmetrics-text')
        firstBytes.length > 0
        endpoint.lastScrapeSize > 0
        second.body().bytes.length > 0
    }
}
