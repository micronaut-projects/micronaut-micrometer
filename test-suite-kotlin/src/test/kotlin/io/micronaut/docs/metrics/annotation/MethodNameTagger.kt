package io.micronaut.docs.metrics.annotation

// tag::imports[]
import io.micrometer.core.instrument.Tag
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class MethodNameTagger : AbstractMethodTagger() {
    override fun buildTags(context: MethodInvocationContext<Any, Any>): List<Tag> =
        listOf(Tag.of("method", context.methodName))
}
// end::class[]
