package io.micronaut.docs.metrics.testing;

// tag::imports[]
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.micronaut.micrometer.test.MeterRegistryAssert;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
// end::imports[]

// tag::class[]
@MicronautTest
class BookServiceTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    TestObservationRegistry observationRegistry;

    @Inject
    BookService bookService;

    @Test
    void recordsMetricsAndObservations() {
        bookService.find("Dune");

        MeterRegistryAssert.assertThat(meterRegistry)
            .hasTimerWithNameAndTags("books.find", Tags.of("exception", "none"), 1);
        TestObservationRegistryAssert.assertThat(observationRegistry)
            .hasObservationWithNameEqualTo("books.lookup");
    }
}
// end::class[]
