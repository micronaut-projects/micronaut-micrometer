package io.micronaut.configuration.metrics.micrometer.prometheus

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class PrometheusExportDefaultsSpec extends Specification {

    void "the prometheus registry follows the export defaults unless it is configured explicitly"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(config)

        expect:
        ctx.containsBean(PrometheusMeterRegistry) == enabled

        cleanup:
        ctx.close()

        where:
        config                                                                                                          | enabled
        [:]                                                                                                             | true
        ['micronaut.metrics.export.defaults.enabled': false]                                                            | false
        ['micronaut.metrics.export.defaults.enabled': false, 'micronaut.metrics.export.prometheus.enabled': true]       | true
        ['micronaut.metrics.export.prometheus.enabled': false]                                                          | false
    }

    void "a simple meter registry is used when all exporters are disabled"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.metrics.export.defaults.enabled': false])
        CompositeMeterRegistry registry = ctx.getBean(CompositeMeterRegistry)

        expect:
        registry.registries*.class == [SimpleMeterRegistry]
        ctx.getBean(MeterRegistry).counter('test').increment() == null

        cleanup:
        ctx.close()
    }
}
