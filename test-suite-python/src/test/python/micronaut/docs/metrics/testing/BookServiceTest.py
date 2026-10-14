# tag::imports[]
from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry, Tags
from io.micrometer.observation.tck import TestObservationRegistry, TestObservationRegistryAssert
from jakarta.inject import Inject
from micronaut.micrometer.test import MeterRegistryAssert
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .BookService import BookService
# end::imports[]


# tag::class[]
@MicronautTest
class BookServiceTest:
    meterRegistry: Annotated[MeterRegistry, Inject]
    observationRegistry: Annotated[TestObservationRegistry, Inject]
    bookService: Annotated[BookService, Inject]

    @Test
    def test_records_metrics_and_observations(self):
        self.bookService.find("Dune")

        MeterRegistryAssert.assertThat(self.meterRegistry) \
            .hasTimerWithNameAndTags("books.find", Tags.of("exception", "none"), 1)
        TestObservationRegistryAssert.assertThat(self.observationRegistry) \
            .hasObservationWithNameEqualTo("books.lookup")
# end::class[]
