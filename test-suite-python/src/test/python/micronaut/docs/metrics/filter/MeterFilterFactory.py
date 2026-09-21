from micronaut.context.annotation import Requires

# tag::imports[]
from io.micrometer.core.instrument import Tag
from io.micrometer.core.instrument.config import MeterFilter
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Factory
# end::imports[]


@Requires(property="spec.name", value="MeterFilterFactoryTest")
# tag::class[]
@Factory
class MeterFilterFactory:

    @Bean
    @Singleton
    def add_common_tag_filter(self) -> MeterFilter:
        """Add global tags to all metrics."""
        return MeterFilter.commonTags([Tag.of("scope", "demo")])

    @Bean
    @Singleton
    def rename_filter(self) -> MeterFilter:
        """Rename a tag key for every metric beginning with a given prefix.

        This will rename the metric name http.server.requests tag value called `method` to `httpmethod`

        OLD: http.server.requests ['method':'GET", ...]
        NEW: http.server.requests ['httpmethod':'GET", ...]
        """
        return MeterFilter.renameTag("http.server.requests", "method", "httpmethod")
# end::class[]
