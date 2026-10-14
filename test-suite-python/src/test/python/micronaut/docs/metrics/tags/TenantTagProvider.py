from micronaut.context.annotation import Requires

# tag::imports[]
from io.micrometer.core.instrument import Tag
from jakarta.inject import Singleton
from java.lang import Throwable
from micronaut.configuration.metrics.binder.web import HttpMetricsTagProvider
from micronaut.http import HttpRequest, HttpResponse
# end::imports[]


@Requires(property="spec.name", value="TenantTagProviderTest")
# tag::class[]
@Singleton
class TenantTagProvider(HttpMetricsTagProvider):

    def getTags(self, request: HttpRequest, response: HttpResponse | None, throwable: Throwable | None) -> list[Tag]:
        tenant = request.getHeaders().get("X-Tenant")
        return [Tag.of("tenant", tenant if tenant is not None else "none")]
# end::class[]
