package io.micronaut.configuration.metrics.binder.r2dbc

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.r2dbc.pool.PoolMetrics
import spock.lang.Specification

class R2dbcPoolMetricsBinderSpec extends Specification {

    private MeterRegistry meterRegistry = Mock(MeterRegistry)

    void "test registry metrics"() {
        given:
        PoolMetrics poolMetrics = Mock(PoolMetrics)
        R2dbcPoolMetricsBinder r2dbcPoolMetricsBinder = new R2dbcPoolMetricsBinder(
                poolMetrics,
                "foo",
                []
        )

        when:
        r2dbcPoolMetricsBinder.bindTo(meterRegistry)

        then:
        1 * meterRegistry.gauge('r2dbc.pool.acquired', Tags.of("name", "foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.allocated', Tags.of("name", "foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.idle', Tags.of("name", "foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.pending', Tags.of("name", "foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.max.allocated', Tags.of("name", "foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.max.pending', Tags.of("name", "foo"), poolMetrics, _)
        0 * _._
    }

    void "test do not registry metrics"() {
        given:
        R2dbcPoolMetricsBinder r2dbcPoolMetricsBinder = new R2dbcPoolMetricsBinder(
                null,
                "foo",
                []
        )

        when:
        r2dbcPoolMetricsBinder.bindTo(meterRegistry)

        then:
        0 * _._
    }
}
