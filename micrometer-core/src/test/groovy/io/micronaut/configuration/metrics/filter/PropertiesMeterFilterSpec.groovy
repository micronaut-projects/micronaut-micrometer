package io.micronaut.configuration.metrics.filter

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

class PropertiesMeterFilterSpec extends Specification {

    void "the filter does not exist when nothing is configured"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        !ctx.containsBean(PropertiesMeterFilter)

        cleanup:
        ctx.close()
    }

    void "the filter does not exist when metrics are disabled"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.enabled'           : false,
                'micronaut.metrics.enable.jvm'        : false
        ])

        expect:
        !ctx.containsBean(PropertiesMeterFilter)

        cleanup:
        ctx.close()
    }

    void "meters can be disabled by name prefix and the most specific prefix wins"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.enable.jvm'       : false,
                'micronaut.metrics.enable.jvm.memory': true,
                'micronaut.metrics.enable.custom.one': 'false'
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        registry.counter('custom.one').increment()
        registry.counter('custom.one.child').increment()
        registry.counter('custom.onemore').increment()
        registry.counter('custom.two').increment()

        then:
        ctx.containsBean(PropertiesMeterFilter)
        registry.find('jvm.memory.used').gauges()
        !registry.find('jvm.threads.live').gauges()
        !registry.find('jvm.classes.loaded').gauges()
        !registry.find('custom.one').counter()
        !registry.find('custom.one.child').counter()
        registry.find('custom.onemore').counter()
        registry.find('custom.two').counter()

        cleanup:
        ctx.close()
    }

    void "the all key disables every meter that is not enabled explicitly"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.enable.all'   : false,
                'micronaut.metrics.enable.custom': true
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        registry.counter('custom.one').increment()
        registry.counter('other.one').increment()

        then:
        registry.find('custom.one').counter()
        !registry.find('other.one').counter()
        !registry.find('jvm.memory.used').gauges()

        cleanup:
        ctx.close()
    }

    void "distribution settings are applied to timers by name prefix"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.distribution.percentiles-histogram.all'                   : true,
                'micronaut.metrics.distribution.percentiles-histogram.no.histogram'          : false,
                'micronaut.metrics.distribution.percentiles.my.timer'                        : '0.5,0.95',
                'micronaut.metrics.distribution.percentiles.my.timer.list'                   : [0.25, 0.75, 0.99],
                'micronaut.metrics.distribution.slo.my.timer'                                : '100ms,1s',
                'micronaut.metrics.distribution.slo.my.timer.list'                           : ['50', 'PT0.2S'],
                'micronaut.metrics.distribution.minimum-expected-value.my'                   : '5ms',
                'micronaut.metrics.distribution.maximum-expected-value.my'                   : '10s',
                'micronaut.metrics.distribution.expiry.my'                                   : '30s',
                'micronaut.metrics.distribution.buffer-length.my'                            : 7
        ])
        PropertiesMeterFilter filter = ctx.getBean(PropertiesMeterFilter)

        when:
        DistributionStatisticConfig config = filter.configure(id('my.timer', Meter.Type.TIMER), DistributionStatisticConfig.NONE)

        then:
        config.percentileHistogram
        config.percentiles == [0.5d, 0.95d] as double[]
        config.serviceLevelObjectiveBoundaries == [TimeUnit.MILLISECONDS.toNanos(100), TimeUnit.SECONDS.toNanos(1)] as double[]
        config.minimumExpectedValueAsDouble == TimeUnit.MILLISECONDS.toNanos(5) as double
        config.maximumExpectedValueAsDouble == TimeUnit.SECONDS.toNanos(10) as double
        config.expiry == Duration.ofSeconds(30)
        config.bufferLength == 7

        when: 'yaml style lists and plain numbers (milliseconds for timers) are used'
        config = filter.configure(id('my.timer.list', Meter.Type.TIMER), DistributionStatisticConfig.NONE)

        then:
        config.percentiles == [0.25d, 0.75d, 0.99d] as double[]
        config.serviceLevelObjectiveBoundaries == [TimeUnit.MILLISECONDS.toNanos(50), TimeUnit.MILLISECONDS.toNanos(200)] as double[]

        when: 'a more specific key overrides all'
        config = filter.configure(id('no.histogram', Meter.Type.TIMER), DistributionStatisticConfig.NONE)

        then:
        !config.percentileHistogram
        config.percentiles == null
        config.serviceLevelObjectiveBoundaries == null

        when: 'existing settings of the meter are kept when nothing is configured for them'
        config = filter.configure(id('other', Meter.Type.TIMER), DistributionStatisticConfig.builder().percentiles(0.9d).build())

        then:
        config.percentileHistogram
        config.percentiles == [0.9d] as double[]

        cleanup:
        ctx.close()
    }

    void "distribution summaries use plain numbers and ignore durations"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.distribution.slo.my.summary'                  : '10,100,1s',
                'micronaut.metrics.distribution.minimum-expected-value.my.summary': '2',
                'micronaut.metrics.distribution.maximum-expected-value.my.summary': '1s'
        ])
        PropertiesMeterFilter filter = ctx.getBean(PropertiesMeterFilter)

        when:
        DistributionStatisticConfig config = filter.configure(id('my.summary', Meter.Type.DISTRIBUTION_SUMMARY), DistributionStatisticConfig.NONE)

        then:
        config.serviceLevelObjectiveBoundaries == [10d, 100d] as double[]
        config.minimumExpectedValueAsDouble == 2d
        config.maximumExpectedValueAsDouble == null

        when: 'other meter types get no values'
        config = filter.configure(id('my.summary', Meter.Type.GAUGE), DistributionStatisticConfig.NONE)

        then:
        config.serviceLevelObjectiveBoundaries == null
        config.minimumExpectedValueAsDouble == null

        cleanup:
        ctx.close()
    }

    void "distribution settings are applied to meters registered in the registry"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.distribution.percentiles.my'  : '0.5,0.99',
                'micronaut.metrics.distribution.slo.my.summary'  : '10,20'
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Timer timer = registry.timer('my.timer')
        timer.record(Duration.ofMillis(10))
        DistributionSummary summary = registry.summary('my.summary')
        summary.record(15)

        then:
        timer.takeSnapshot().percentileValues()*.percentile() == [0.5d, 0.99d]
        summary.takeSnapshot().histogramCounts()*.bucket() == [10d, 20d]

        cleanup:
        ctx.close()
    }

    void "the filter is applied after the http shortcut filter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.binders.web.server.percentiles'                     : '0.9',
                'micronaut.metrics.distribution.percentiles.http.server.requests'      : '0.5',
                'micronaut.metrics.distribution.percentiles-histogram.http.server'     : true
        ])
        List<MeterFilter> filters = ctx.getBeansOfType(MeterFilter).toList()
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Timer timer = registry.timer('http.server.requests')
        timer.record(Duration.ofMillis(10))

        then:
        filters.last() instanceof PropertiesMeterFilter
        timer.takeSnapshot().percentileValues()*.percentile() == [0.5d]

        cleanup:
        ctx.close()
    }

    void "invalid values fail fast"() {
        when:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.distribution.slo.my.timer': 'not-a-duration'
        ])
        ctx.getBean(PropertiesMeterFilter)

        then:
        BeanInstantiationException e = thrown()
        e.cause instanceof ConfigurationException || e.message.contains('not-a-duration')

        cleanup:
        ctx?.close()
    }

    private static Meter.Id id(String name, Meter.Type type) {
        new Meter.Id(name, Tags.empty(), null, null, type)
    }
}
