package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;

import java.util.List;

public interface TagsBasedOnMethodInvocationContext {

    public List<Tag> buildTags(MethodInvocationContext<Object, Object> context);
}
