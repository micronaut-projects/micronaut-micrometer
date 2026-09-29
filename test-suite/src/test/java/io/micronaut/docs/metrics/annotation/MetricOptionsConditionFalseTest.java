package io.micronaut.docs.metrics.annotation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = "property", value = "false")
@MicronautTest
class MetricOptionsConditionFalseTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    MetricOptionsConditionExample conditionExample;

    @Test
    void metricIsNotPublishedWhenTheConditionIsFalse() {
        conditionExample.doSomething();

        assertThrows(MeterNotFoundException.class, () -> meterRegistry.get("do_something_conditionally").timer());
    }
}
