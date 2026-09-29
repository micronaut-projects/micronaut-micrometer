package io.micronaut.docs.metrics.annotation

// tag::imports[]
import io.micrometer.core.instrument.Tag
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class MethodNameTagger extends AbstractMethodTagger {
    @Override
    List<Tag> buildTags(MethodInvocationContext<Object, Object> context) {
        [Tag.of("method", context.methodName)]
    }
}
// end::class[]
