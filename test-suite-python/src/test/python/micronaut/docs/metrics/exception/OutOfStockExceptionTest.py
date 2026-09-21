from java.lang import Integer
from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="OutOfStockExceptionTest")
@MicronautTest
class OutOfStockExceptionTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    meterRegistry: Annotated[MeterRegistry, Inject]

    @Test
    def test_the_handled_exception_is_reported_with_the_status_of_its_response(self):
        response = self.client.toBlocking().exchange(HttpRequest.GET("/stock/apples"), Integer)

        assert response.getStatus() == HttpStatus.OK
        assert response.body() == 0

        timer = self.meterRegistry.get("http.server.requests") \
            .tags("uri", "/stock/{name}", "exception", "OutOfStockException", "status", "200") \
            .timer()
        assert timer.count() == 1
