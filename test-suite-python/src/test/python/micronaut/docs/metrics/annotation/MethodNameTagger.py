# tag::imports[]
from jakarta.inject import Singleton
from micronaut.aop import MethodInvocationContext
from micronaut.configuration.metrics.aggregator import AbstractMethodTagger

try:
    from io.micrometer.core.instrument import Tag
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from micrometer.core.instrument import Tag
# end::imports[]


# tag::class[]
@Singleton
class MethodNameTagger(AbstractMethodTagger):
    def buildTags(self, context: MethodInvocationContext) -> list[Tag]:
        return [Tag.of("method", context.getMethodName())]
# end::class[]
