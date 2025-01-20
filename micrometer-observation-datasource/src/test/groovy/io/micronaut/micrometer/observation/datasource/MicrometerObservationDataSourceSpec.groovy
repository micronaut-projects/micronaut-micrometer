package io.micronaut.micrometer.observation.datasource

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import io.micrometer.tracing.Tracer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Internal
import jakarta.inject.Singleton
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import net.ttddyy.observation.tracing.DataSourceBaseObservationHandler
import spock.lang.Specification

import javax.sql.DataSource

class MicrometerObservationDataSourceSpec extends Specification {

    void 'test metrics and tracer'() {
        when:
        def context = ApplicationContext.run([
                'micronaut.application.name'                                         : 'ds-observation',
                'spec.name'                                                          : 'datasource-observation',
                'custom.builder'                                                     : 'true',
                'datasources.default.dialect'                                        : 'H2',
                'datasources.default.schema-generate'                                : 'CREATE_DROP',
                'datasources.default.url'                                            : 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.default.username'                                       : 'sa',
                'datasources.default.driver-class-name'                              : 'org.h2.Driver',
                'micrometer.observation.datasource.enabled'                          : 'true',
                'micrometer.observation.datasource.listener.include-parameter-values': 'true'
        ])
        def productRepository = context.getBean(ProductRepository)
        def product = productRepository.save(new Product(null, 'Soccer Ball'))
        product = productRepository.findById(product.id()).orElse(null)
        def productName = product.name()

        then:
        context.getBeansOfType(DataSourceBeanCreatedEventListener).size() == 1
        context.getBeansOfType(DataSource).size() == 1
        context.getBeansOfType(Tracer).size() == 1
        context.getBeansOfType(ObservationDataSourceConfig).size() == 1
        def registry = context.getBean(ObservationRegistry)
        registry
        if (registry instanceof TestObservationRegistry) {
            def testRegistry = (TestObservationRegistry) registry
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsEqualTo(9)
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo('jdbc.connection', 3)
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo('jdbc.query', 4)
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo('jdbc.result-set', 2)
            TestObservationRegistryAssert.assertThat(testRegistry).hasAnObservation { it ->
                it.hasNameEqualTo('jdbc.query')
                        .hasContextualNameEqualTo('query')
                        .hasHighCardinalityKeyValue('jdbc.query[0]', 'DROP TABLE `mn_product`')
                        .hasError()
            }
            TestObservationRegistryAssert.assertThat(testRegistry).hasAnObservation { it ->
                it.hasNameEqualTo('jdbc.query')
                        .hasHighCardinalityKeyValue('jdbc.query[0]', 'INSERT INTO `mn_product` (`name`) VALUES (?)')
                        .hasHighCardinalityKeyValue('jdbc.params[0]', '(Soccer Ball)')
                        .hasContextualNameEqualTo('query')
                        .doesNotHaveError()
            }
            TestObservationRegistryAssert.assertThat(testRegistry).hasObservationWithNameEqualTo('jdbc.result-set')
                    .that()
                    .hasContextualNameEqualTo('result-set')
                    .doesNotHaveError()
                    .hasHighCardinalityKeyValueWithKey('jdbc.row-count')
        }
        productName == 'Soccer Ball'

        cleanup:
        context.close()
    }

    void 'test metrics and tracer disabled result set tracing'() {
        when:
        def context = ApplicationContext.run([
                'micronaut.application.name'                                         : 'ds-observation',
                'spec.name'                                                          : 'datasource-observation',
                'custom.builder'                                                     : 'false',
                'datasources.default.dialect'                                        : 'H2',
                'datasources.default.schema-generate'                                : 'CREATE_DROP',
                'datasources.default.url'                                            : 'jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.default.username'                                       : 'sa',
                'datasources.default.driver-class-name'                              : 'org.h2.Driver',
                'micrometer.observation.datasource.enabled'                          : 'true',
                'micrometer.observation.datasource.listener.supported-types'         : ['QUERY', 'CONNECTION'],
                'micrometer.observation.datasource.listener.include-parameter-values': 'false'
        ])

        def productRepository = context.getBean(ProductRepository)
        productRepository.save(new Product(null, 'Watch'))
        def product = productRepository.findByName('Watch').orElse(null)
        def productId = product.id()
        def count = productRepository.count()

        then:
        context.getBeansOfType(DataSourceBeanCreatedEventListener).size() == 1
        context.getBeansOfType(DataSource).size() == 1
        context.getBeansOfType(Tracer).size() == 1
        context.getBeansOfType(ObservationDataSourceConfig).size() == 1
        product.id() == productId
        count == 1
        def registry = context.getBean(ObservationRegistry)
        registry
        if (registry instanceof TestObservationRegistry) {
            def testRegistry = (TestObservationRegistry) registry
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsEqualTo(9)
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo('jdbc.connection', 4)
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo('jdbc.query', 5)
            TestObservationRegistryAssert.assertThat(testRegistry).hasAnObservation { it ->
                it.hasNameEqualTo('jdbc.query')
                        .hasContextualNameEqualTo('query')
                        .hasHighCardinalityKeyValue('jdbc.query[0]', 'DROP TABLE `mn_product`')
                        //.doesNotHaveError()
            }
            TestObservationRegistryAssert.assertThat(testRegistry).hasAnObservation { it ->
                it.hasNameEqualTo('jdbc.query')
                        .hasHighCardinalityKeyValue('jdbc.query[0]', 'INSERT INTO `mn_product` (`name`) VALUES (?)')
                        .hasContextualNameEqualTo('query')
                        .doesNotHaveError()
            }
            TestObservationRegistryAssert.assertThat(testRegistry).hasAnObservation { it ->
                it.hasNameEqualTo('jdbc.query')
                        .hasHighCardinalityKeyValue('jdbc.query[0]', 'SELECT COUNT(*) FROM `mn_product` product_')
                        .hasContextualNameEqualTo('query')
                        .doesNotHaveError()
            }
        }

        cleanup:
        context.close()
    }

    @Factory
    @Requires(property = 'spec.name', value = 'datasource-observation')
    @Internal
    static class DefaultFactory {

        @Singleton
        @Primary
        ObservationRegistry observationRegistry(List<DataSourceBaseObservationHandler> handlers) {
            ObservationRegistry observationRegistry = TestObservationRegistry.create()
            for (DataSourceBaseObservationHandler handler : handlers) {
                observationRegistry.observationConfig().observationHandler(handler)
            }
            return observationRegistry
        }

        @Singleton
        Tracer tracer() {
            return Tracer.NOOP
        }

        @Singleton
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry()
        }
    }

    @Factory
    @Requires(property = 'custom.builder', value = 'true')
    @Internal
    static class BuilderFactory {
        @Singleton
        ProxyDataSourceBuilder builder() {
            return new ProxyDataSourceBuilder()
                    .proxyGeneratedKeys()
                    .proxyResultSet()
                    .asJson()
        }
    }

}
