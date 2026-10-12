package io.micronaut.configuration.metrics.binder.system

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class DiskSpaceMetricsBinderSpec extends Specification {

    @TempDir
    Path tempDir

    void "disk space metrics are reported for the working directory by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        expect:
        ctx.containsBean(DiskSpaceMetricsBinder)
        registry.find('disk.free').tag('path', new File('.').absolutePath).gauge().value() > 0
        registry.find('disk.total').gauges().size() == 1

        cleanup:
        ctx.close()
    }

    void "disk space metrics are reported for each configured path"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.binders.disk-space.paths': ['.', tempDir.toString(), tempDir.toString()]
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        expect:
        registry.find('disk.free').gauges()*.id*.getTag('path') as Set == [new File('.').absolutePath, tempDir.toFile().absolutePath] as Set

        cleanup:
        ctx.close()
    }

    void "disk space metrics can be disabled: #config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(config)

        expect:
        !ctx.containsBean(DiskSpaceMetricsBinder)
        ctx.findBean(MeterRegistry).map { it.find('disk.free').gauge() == null }.orElse(true)

        cleanup:
        ctx.close()

        where:
        config << [['micronaut.metrics.binders.disk-space.enabled': false], ['micronaut.metrics.enabled': false]]
    }
}
