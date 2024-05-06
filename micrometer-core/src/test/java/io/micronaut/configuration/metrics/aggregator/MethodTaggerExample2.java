package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class MethodTaggerExample2 implements MethodTagger {

    @Override
    public List<Tag> buildTags(MethodInvocationContext<Object, Object> context) {
        return List.of(Tag.of("parameters", String.join(" ", context.getParameterValueMap().keySet())));
    }
}
