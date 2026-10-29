package io.micronaut.configuration.metrics.binder.datasource

import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import spock.lang.Specification

import javax.sql.DataSource

class DataSourcePoolMetricsBinderFactorySpec extends Specification {

    void "test getting the beans manually"() {
        when:
        def meterBinder = new DataSourcePoolMetricsBinderFactory().dataSourceMeterBinder("foo", new Foo())

        then:
        meterBinder
    }

    class Foo implements DataSourcePoolMetadata {
        DataSource getDataSource() { null }
        Integer getIdle() { null }
        Float getUsage() { null }
        Integer getActive() { null }
        Integer getMax() { null }
        Integer getMin() { null }
        String getValidationQuery() { null }
        Boolean getDefaultAutoCommit() { null }
    }
}
