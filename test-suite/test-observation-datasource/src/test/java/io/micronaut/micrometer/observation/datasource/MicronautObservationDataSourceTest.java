package io.micronaut.micrometer.observation.datasource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.micrometer.tracing.Tracer;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.ttddyy.observation.tracing.DataSourceBaseObservationHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;


@MicronautTest
@Property(name = "spec.name", value = "micrometer-ds-native-test")
class MicronautObservationDataSourceTest {

    @Inject
    ProductRepository productRepository;

    @Inject
    ObservationRegistry registry;

    @Test
    void testObservationDs() {
        Product product = productRepository.save(new Product(null, "Soccer Ball"));
        product = productRepository.findById(product.id()).orElse(null);
        Assertions.assertNotNull(product);
        String productName = product.name();
        Assertions.assertEquals("Soccer Ball", productName);

        Assertions.assertInstanceOf(ObservationRegistry.class, registry);
        if (registry instanceof TestObservationRegistry testRegistry) {
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsEqualTo(8);
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo("jdbc.connection", 2);
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo("jdbc.query", 4);
            TestObservationRegistryAssert.assertThat(testRegistry).hasNumberOfObservationsWithNameEqualTo("jdbc.result-set", 2);
            TestObservationRegistryAssert.assertThat(testRegistry).hasAnObservation(it ->
                it.hasNameEqualTo("jdbc.query")
                    .hasContextualNameEqualTo("query")
                    .hasHighCardinalityKeyValue("jdbc.query[0]", "DROP TABLE `mn_product`")
                    .hasError());
            TestObservationRegistryAssert.assertThat(testRegistry).hasAnObservation( it ->
                it.hasNameEqualTo("jdbc.query")
                    .hasHighCardinalityKeyValue("jdbc.query[0]", "INSERT INTO `mn_product` (`name`) VALUES (?)")
                    .hasHighCardinalityKeyValue("jdbc.params[0]", "(Soccer Ball)")
                    .hasContextualNameEqualTo("query")
                    .doesNotHaveError());
            TestObservationRegistryAssert.assertThat(testRegistry).hasObservationWithNameEqualTo("jdbc.result-set")
                .that()
                .hasContextualNameEqualTo("generated-keys")
                .doesNotHaveError()
                .hasHighCardinalityKeyValueWithKey("jdbc.row-count");
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "micrometer-ds-native-test")
    @Internal
    static class DefaultFactory {

        @Singleton
        @Primary
        ObservationRegistry observationRegistry(List<DataSourceBaseObservationHandler> handlers) {
            ObservationRegistry observationRegistry = TestObservationRegistry.create();
            for (DataSourceBaseObservationHandler handler : handlers) {
                observationRegistry.observationConfig().observationHandler(handler);
            }
            return observationRegistry;
        }

        @Singleton
        Tracer tracer() {
            return Tracer.NOOP;
        }

        @Singleton
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
