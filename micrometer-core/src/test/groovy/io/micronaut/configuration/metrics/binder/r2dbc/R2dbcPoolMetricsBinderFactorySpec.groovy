package io.micronaut.configuration.metrics.binder.r2dbc

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.PoolMetrics
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.reactivestreams.Publisher
import spock.lang.Specification

class R2dbcPoolMetricsBinderFactorySpec extends Specification {

    void "test getting metrics from r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def pool = Mock(ConnectionPool)
        def metrics = new StubMetrics()
        pool.getMetrics() >> Optional.of(metrics)

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", pool)

        then:
        meterBinder.@poolMetrics == metrics
    }

    void "test getting empty metrics from r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def pool = Mock(ConnectionPool)
        pool.getMetrics() >> Optional.empty()

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", pool)

        then:
        meterBinder.@poolMetrics == null
    }

    void "test ignoring metrics when factory is not r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def factory = Mock(StubFactory)

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", factory)

        then:
        meterBinder.@poolMetrics == null
    }

    class StubFactory implements ConnectionFactory {
        Publisher<? extends Connection> create() { null }
        ConnectionFactoryMetadata getMetadata() { null }
    }

    class StubMetrics implements PoolMetrics {
        int acquiredSize() { 0 }
        int allocatedSize() { 0 }
        int idleSize() { 0 }
        int pendingAcquireSize() { 0 }
        int getMaxAllocatedSize() { 0 }
        int getMaxPendingAcquireSize() { 0 }
    }
}
