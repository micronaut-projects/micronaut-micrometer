package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class IncorrectMethodTaggerExample implements MethodTagger {

    /**
     * Intentional improper usage for testing it does not stop publishing of metric with other valid tags
     */
    @Override
    public List<Tag> buildTags(MethodInvocationContext<Object, Object> context) {
        return null;
    }
}
