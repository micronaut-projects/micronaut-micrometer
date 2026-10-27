package io.micronaut.configuration.metrics.micrometer

import io.micronaut.configuration.metrics.annotation.RequiresMetricsExport
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import spock.lang.Specification

class MetricsExportConditionSpec extends Specification {

    void "an exporter is enabled when #config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(config + ['spec.name': 'MetricsExportConditionSpec'])

        expect:
        ctx.containsBean(TestExporter) == enabled

        cleanup:
        ctx.close()

        where:
        config                                                                                                         | enabled
        [:]                                                                                                            | true
        ['micronaut.metrics.export.defaults.enabled': true]                                                            | true
        ['micronaut.metrics.export.defaults.enabled': false]                                                           | false
        ['micronaut.metrics.export.test.enabled': false]                                                               | false
        ['micronaut.metrics.export.test.enabled': true]                                                                | true
        ['micronaut.metrics.export.defaults.enabled': false, 'micronaut.metrics.export.test.enabled': true]            | true
        ['micronaut.metrics.export.defaults.enabled': true, 'micronaut.metrics.export.test.enabled': false]            | false
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MetricsExportConditionSpec')
    @RequiresMetricsExport('micronaut.metrics.export.test.enabled')
    static class TestExporter {
    }
}
