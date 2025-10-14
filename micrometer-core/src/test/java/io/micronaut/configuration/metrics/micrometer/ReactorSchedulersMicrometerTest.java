package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "endpoints.metrics.sensitive", value = "false")
@Property(name = MICRONAUT_METRICS_ENABLED, value = "true")
@Property(name = "spec.name", value = "ReactorSchedulersMicrometerTest")
@MicronautTest
class ReactorSchedulersMicrometerTest {

    static {
        Schedulers.resetFactory();
        Schedulers.enableMetrics();
    }

    @Inject
    SimpleMeterRegistry simpleMeterRegistry;

    @Test
    void enablingReactorSchedulersMetricsExposesSchedulerMetrics() throws InterruptedException {

        // Create a Reactor Scheduler and schedule some delayed tasks
        Scheduler scheduler = Schedulers.newParallel("test", 2);
        Scheduler.Worker worker = scheduler.createWorker();
        try {
            for (int i = 0; i < 5; i++) {
                worker.schedule(() -> { /* no-op */ }, 10, TimeUnit.MILLISECONDS);
            }
        } finally {
            worker.dispose();
        }

        // Exercise a reactive pipeline on the instrumented scheduler
        String result = Mono.just("demo")
            .delayElement(Duration.ofMillis(5))
            .subscribeOn(scheduler)
            .map(s -> s)
            .block();

        // Poll for meters to appear (registration can be asynchronous)
        boolean observed = false;
        for (int i = 0; i < 40 && !observed; i++) {
            Set<String> names = simpleMeterRegistry.getMeters().stream()
                .map(m -> m.getId().getName())
                .collect(Collectors.toSet());
            if (names.contains("executor.scheduled.once")) {
                observed = true;
                break;
            }
            Thread.sleep(100);
        }

        assertEquals("demo", result);
        assertTrue(observed);

        // Cleanup
        scheduler.dispose();
        Schedulers.disableMetrics();
    }

    @Factory
    static class TestBeans {

        @Requires(property = "spec.name", value = "ReactorSchedulersMicrometerTest")
        @Singleton
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
