# tag::imports[]
from io.micrometer.common.annotation import ValueResolver
from jakarta.inject import Singleton
# end::imports[]


# tag::class[]
@Singleton
class RegionResolver(ValueResolver):

    def resolve(self, parameter) -> str:
        return parameter.region
# end::class[]
