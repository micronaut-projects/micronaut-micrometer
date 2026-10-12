package io.micronaut.configuration.metrics.binder.health

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.health.CurrentHealthStatus
import io.micronaut.health.HealthStatus
import spock.lang.Specification

class HealthStatusMetricsBinderSpec extends Specification {

    void "the health gauge is disabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        !ctx.containsBean(HealthStatusMetricsBinder)
        !ctx.getBean(MeterRegistry).find('health.status').gauge()

        cleanup:
        ctx.close()
    }

    void "the health gauge reports the current health status"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.metrics.binders.health.enabled': true])
        CurrentHealthStatus currentHealthStatus = ctx.getBean(CurrentHealthStatus)
        Gauge gauge = ctx.getBean(MeterRegistry).get('health.status').gauge()

        expect:
        gauge.value() == 3d

        when:
        currentHealthStatus.update(HealthStatus.DOWN)

        then:
        gauge.value() == 1d

        when:
        currentHealthStatus.update(new HealthStatus('OUT_OF_SERVICE'))

        then:
        gauge.value() == 2d

        when:
        currentHealthStatus.update(HealthStatus.UNKNOWN)

        then:
        gauge.value() == 0d

        cleanup:
        ctx.close()
    }

    void "statuses are mapped to values"() {
        expect:
        HealthStatusMetricsBinder.toValue(status) == value

        where:
        status                                                    | value
        HealthStatus.UP                                           | 3d
        new HealthStatus('up')                                    | 3d
        new HealthStatus('OUT_OF_SERVICE')                        | 2d
        HealthStatus.DOWN                                         | 1d
        new HealthStatus('BROKEN', null, false, null)             | 1d
        HealthStatus.UNKNOWN                                      | 0d
        new HealthStatus('CUSTOM')                                | 0d
    }

    void "the health gauge is not created when metrics are disabled"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.enabled'               : false,
                'micronaut.metrics.binders.health.enabled': true
        ])

        expect:
        !ctx.containsBean(HealthStatusMetricsBinder)

        cleanup:
        ctx.close()
    }
}
