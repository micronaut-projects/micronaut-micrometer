package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class OrderedMethodTagger extends AbstractMethodTagger{
    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public List<Tag> buildTags(MethodInvocationContext<Object, Object> context) {
        return List.of(Tag.of("ordered", String.valueOf(getOrder())));
    }
}
