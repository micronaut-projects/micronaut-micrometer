from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Get


@Requires(property="spec.name", value="TenantTagProviderTest")
@Controller("/tenants")
class TenantController:

    @Get
    def index(self) -> str:
        return "ok"
