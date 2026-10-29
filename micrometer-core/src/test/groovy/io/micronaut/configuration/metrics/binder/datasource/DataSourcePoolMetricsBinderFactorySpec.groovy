package io.micronaut.configuration.metrics.binder.datasource

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import spock.lang.Specification

import javax.sql.DataSource

class DataSourcePoolMetricsBinderFactorySpec extends Specification {

    void "registries bound after startup also receive pool metrics"() {
        given:
        DataSource dataSource = Stub()
        DataSourcePoolMetadata metadata = Stub() {
            getDataSource() >> dataSource
            getActive() >> 1
        }
        ApplicationContext context = ApplicationContext.builder(['micronaut.metrics.enabled': true])
                .singletons(metadata).start()
        SimpleMeterRegistry registry = new SimpleMeterRegistry()

        when:
        context.getBean(DataSourcePoolMetricsBinderFactory).dataSourceMeterBinder(context).bindTo(registry)

        then:
        context.getBean(MeterRegistry).get('jdbc.connections.active').gauge().value() == 1
        registry.get('jdbc.connections.active').gauge().value() == 1

        cleanup:
        registry.close()
        context.close()
    }

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
