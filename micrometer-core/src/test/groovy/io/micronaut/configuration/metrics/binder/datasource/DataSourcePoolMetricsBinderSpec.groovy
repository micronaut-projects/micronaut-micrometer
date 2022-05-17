package io.micronaut.configuration.metrics.binder.datasource

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import spock.lang.Specification

import javax.sql.DataSource

class DataSourcePoolMetricsBinderSpec extends Specification {

    private MeterRegistry meterRegistry = Mock(MeterRegistry)

    void "DataSourcePoolMetricsBinder"() {
        given:
        DataSource dataSource = Mock(DataSource)
        DataSourcePoolMetricsBinder dataSourcePoolMetricsBinder = new DataSourcePoolMetricsBinder(
                dataSource,
                new FooDataSourcePoolMetadata(dataSource),
                "foo",
                []
        )

        when:
        dataSourcePoolMetricsBinder.bindTo(meterRegistry)

        then:
        1 * meterRegistry.gauge('jdbc.connections.active', Tags.of("name", "foo"), dataSource, _)
        1 * meterRegistry.gauge('jdbc.connections.min', Tags.of("name", "foo"), dataSource, _)
        1 * meterRegistry.gauge('jdbc.connections.max', Tags.of("name", "foo"), dataSource, _)
        1 * meterRegistry.gauge('jdbc.connections.usage', Tags.of("name", "foo"), dataSource, _)
        0 * _._

    }

    class FooDataSourcePoolMetadata implements DataSourcePoolMetadata {

        final DataSource dataSource

        FooDataSourcePoolMetadata(DataSource dataSource) {
            this.dataSource = dataSource
        }

        Integer getIdle() { 0 }
        Float getUsage() { 0 }
        Integer getActive() { 0 }
        Integer getMax() { 0 }
        Integer getMin() { 0 }
        String getValidationQuery() { "SELECT 1" }
        Boolean getDefaultAutoCommit() { null }
    }
}
