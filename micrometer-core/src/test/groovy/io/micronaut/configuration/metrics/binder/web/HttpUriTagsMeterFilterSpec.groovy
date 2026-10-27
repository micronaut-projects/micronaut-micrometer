package io.micronaut.configuration.metrics.binder.web

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import jakarta.inject.Singleton

import java.util.concurrent.TimeUnit
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class HttpUriTagsMeterFilterSpec extends Specification {

    void "the guard is disabled unless a positive limit is configured: #config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(config)

        expect:
        !ctx.containsBean(MeterFilter, Qualifiers.byName('httpServerUriTags'))
        !ctx.containsBean(MeterFilter, Qualifiers.byName('httpClientUriTags'))

        cleanup:
        ctx.close()

        where:
        config << [
                [:],
                ['micronaut.metrics.binders.web.server.max-uri-tags': 0, 'micronaut.metrics.binders.web.client.max-uri-tags': '0'],
                ['micronaut.metrics.binders.web.server.max-uri-tags': -1, 'micronaut.metrics.binders.web.client.max-uri-tags': '-1'],
                ['micronaut.metrics.enabled': false, 'micronaut.metrics.binders.web.server.max-uri-tags': 10, 'micronaut.metrics.binders.web.client.max-uri-tags': 10]
        ]
    }

    void "the server and client guards cap the distinct uri tag values"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.binders.web.server.max-uri-tags': 2,
                'micronaut.metrics.binders.web.client.max-uri-tags': '3'
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        ['/a', '/b', '/c', '/d'].each { uri ->
            registry.timer('http.server.requests', 'uri', uri, 'status', '200').record(1, java.util.concurrent.TimeUnit.MILLISECONDS)
            registry.timer('http.client.requests', 'uri', uri).record(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        // more meters for an existing uri value are still allowed
        registry.timer('http.server.requests', 'uri', '/a', 'status', '500')
        registry.more().longTaskTimer('http.server.requests.active', 'uri', '/b')
        // other meters are not affected
        ['/a', '/b', '/c', '/d'].each { uri -> registry.counter('other.requests', 'uri', uri) }

        then:
        ctx.containsBean(MeterFilter, Qualifiers.byName('httpServerUriTags'))
        ctx.containsBean(MeterFilter, Qualifiers.byName('httpClientUriTags'))
        registry.find('http.server.requests').timers()*.id*.getTag('uri') as Set == ['/a', '/b'] as Set
        registry.find('http.server.requests').timers().size() == 3
        registry.find('http.server.requests.active').longTaskTimer()
        registry.find('http.client.requests').timers()*.id*.getTag('uri') as Set == ['/a', '/b', '/c'] as Set
        registry.find('other.requests').counters().size() == 4

        cleanup:
        ctx.close()
    }

    void "the server guard applies to the http server metrics"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                        : 'HttpUriTagsMeterFilterSpec',
                'micronaut.metrics.binders.web.server.max-uri-tags': 2
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)
        MeterRegistry registry = server.applicationContext.getBean(MeterRegistry)

        when:
        ['one', 'two', 'three'].each { client.toBlocking().retrieve(HttpRequest.GET("/uri-tags/$it")) }

        then:
        registry.find('http.server.requests').timers()*.id*.getTag('uri') as Set == ['/uri-tags/one', '/uri-tags/two'] as Set

        cleanup:
        client.close()
        server.close()
    }

    void "setting only max-uri-tags does not apply the legacy http distribution settings: #side"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                                                     : 'HttpUriTagsMeterFilterSpec.distribution',
                ("micronaut.metrics.binders.web.${side}.max-uri-tags".toString()): 10
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when: 'an earlier filter configures percentiles, a histogram and an SLO'
        Timer timer = registry.timer(meter, 'uri', '/a')
        timer.record(10, TimeUnit.MILLISECONDS)

        then: 'they are not reset by the legacy percentile filter'
        timer.takeSnapshot().percentileValues()*.percentile() == [0.5d]
        timer.takeSnapshot().histogramCounts()*.bucket() == [TimeUnit.MILLISECONDS.toNanos(100) as double]

        cleanup:
        ctx.close()

        where:
        side     | meter
        'server' | 'http.server.requests'
        'client' | 'http.client.requests'
    }

    void "the legacy http distribution settings are still applied when their own keys are set: #key"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                                               : 'HttpUriTagsMeterFilterSpec.distribution',
                'micronaut.metrics.binders.web.server.max-uri-tags'       : 10,
                ("micronaut.metrics.binders.web.server.${key}".toString()): value
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Timer timer = registry.timer('http.server.requests', 'uri', '/a')
        timer.record(10, TimeUnit.MILLISECONDS)

        then: 'the legacy filter is created and replaces the earlier percentiles'
        timer.takeSnapshot().percentileValues()*.percentile() == percentiles

        cleanup:
        ctx.close()

        where:
        key           | value | percentiles
        'percentiles' | '0.9' | [0.9d]
        'histogram'   | true  | []
        'slos'        | '0.1' | []
        'min'         | 0.1   | []
        'max'         | 10    | []
    }

    @Factory
    @Requires(property = 'spec.name', value = 'HttpUriTagsMeterFilterSpec.distribution')
    static class EarlyDistributionFilterFactory {

        @Singleton
        @Order(-100)
        MeterFilter earlyDistributionFilter() {
            new MeterFilter() {
                @Override
                DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                    if (id.name.startsWith('http.')) {
                        return DistributionStatisticConfig.builder()
                                .percentiles(0.5d)
                                .percentilesHistogram(true)
                                .serviceLevelObjectives(TimeUnit.MILLISECONDS.toNanos(100) as double)
                                .build()
                                .merge(config)
                    }
                    config
                }
            }
        }
    }

    @Requires(property = 'spec.name', value = 'HttpUriTagsMeterFilterSpec')
    @Controller('/uri-tags')
    static class UriTagsController {

        @Get('/one')
        String one() { 'one' }

        @Get('/two')
        String two() { 'two' }

        @Get('/three')
        String three() { 'three' }
    }
}
