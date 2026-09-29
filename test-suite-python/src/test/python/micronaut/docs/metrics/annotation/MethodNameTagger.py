# tag::imports[]
from io.micrometer.core.instrument import Tag
from jakarta.inject import Singleton
from micronaut.aop import MethodInvocationContext
from micronaut.configuration.metrics.aggregator import AbstractMethodTagger
# end::imports[]


# tag::class[]
@Singleton
class MethodNameTagger(AbstractMethodTagger):
    def buildTags(self, context: MethodInvocationContext) -> list[Tag]:
        return [Tag.of("method", context.getMethodName())]
# end::class[]
