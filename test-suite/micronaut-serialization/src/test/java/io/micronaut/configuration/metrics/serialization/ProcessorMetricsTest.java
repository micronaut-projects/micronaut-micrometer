package io.micronaut.configuration.metrics.serialization;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProcessorMetricsTest {

    @Test
    void testProcessorMetricsCanBeMeasured() {
        var registry = new SimpleMeterRegistry();
        new ProcessorMetrics().bindTo(registry);

        Assertions.assertNotNull(registry.find("process.cpu.time").functionCounter());
        for (Meter meter : registry.getMeters()) {
            for (Measurement measurement : meter.measure()) {
                measurement.getValue();
            }
        }
    }
}
