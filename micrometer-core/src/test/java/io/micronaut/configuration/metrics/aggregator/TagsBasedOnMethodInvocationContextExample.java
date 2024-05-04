package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class TagsBasedOnMethodInvocationContextExample implements TagsBasedOnMethodInvocationContext{

    @Override
    public List<Tag> buildTags(MethodInvocationContext<Object, Object> context) {
        return List.of(Tag.of("method", context.getMethodName()));
    }
}
