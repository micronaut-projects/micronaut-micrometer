import time
from typing import Annotated

from io.micrometer.core.instrument import MeterRegistry
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .TenantController import TenantController
from .TenantTagProvider import TenantTagProvider


@Property(name="spec.name", value="TenantTagProviderTest")
@MicronautTest
class TenantTagProviderTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    meterRegistry: Annotated[MeterRegistry, Inject]

    @Test
    def test_the_tenant_tag_is_added_to_the_http_metrics(self):
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/tenants").header("X-Tenant", "acme")) == "ok"

        assert self.meterRegistry.find("http.client.requests").tags("tenant", "acme").timer() is not None

        # the server timer is recorded once the response was written
        search = self.meterRegistry.find("http.server.requests").tags("uri", "/tenants", "tenant", "acme")
        deadline = time.monotonic() + 5
        while search.timer() is None and time.monotonic() < deadline:
            time.sleep(0.01)
        timer = search.timer()
        assert timer is not None
        assert timer.count() == 1
