package io.micronaut.docs.metrics.annotation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Property(name = "property", value = "true")
@MicronautTest
class MetricOptionsTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    MetricOptionsFilterTaggersExample filterTaggersExample;

    @Inject
    MetricOptionsConditionExample conditionExample;

    @Test
    void onlyTheSelectedTaggersAreApplied() {
        filterTaggersExample.doSomething();

        var timer = meterRegistry.get("do_something").tags("method", "doSomething").timer();
        assertEquals(1, timer.count());
        assertNull(timer.getId().getTag("parameters"));
    }

    @Test
    void metricIsPublishedWhenTheConditionIsTrue() {
        conditionExample.doSomething();

        var timer = meterRegistry.get("do_something_conditionally").timer();
        assertEquals(1, timer.count());
    }
}
