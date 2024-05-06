package io.micronaut.docs;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class MethodNameTagger extends AbstractMethodTagger {
    @Override
    public List<Tag> buildTags(MethodInvocationContext<Object, Object> context) {
        return Collections.singletonList(Tag.of("method", context.getMethodName()));
    }
}
