package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;

import java.util.Collections;
import java.util.List;

public class DefaultTagsBasedOnMethodInvocationContext implements TagsBasedOnMethodInvocationContext {
    @Override
    public List<Tag> buildTags(MethodInvocationContext<Object, Object> context) {
        return Collections.emptyList();
    }
}
