/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.dev;

import ch.qos.logback.classic.LoggerContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Runs an application with metrics through the development runtime, which restarts it on a change, with the JVM and
 * Logback binders enabled, as they are by default. Both register with a JVM-global object: the JVM binder a
 * notification listener on the garbage collector beans, the Logback binder a turbo filter on the Logback context. They
 * are closed with the context that created them, so neither keeps the registry of a stopped generation, nor, through the
 * meters of that registry, the classes of the application.
 *
 * <p>The registry is created again in each generation rather than retained: see the development mode section of the
 * user guide.</p>
 */
class MeterRegistryReloadTest {

    private static final String BINDER = "example.AppMetrics";
    private static final String TURBO_FILTER = "io.micronaut.configuration.metrics.binder.logging.MicronautLogbackMetrics$MetricsTurboFilter";

    @TempDir
    Path project;

    @Test
    void theRegistryOfARetiredGenerationIsCollectedWithTheJvmAndLogbackBinders() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            binder(harness, "one");
            harness.start();
            MeterRegistry first = harness.context().getBean(MeterRegistry.class);
            assertNotNull(first.find("jvm.memory.used").gauge());
            assertNotNull(first.find("logback.events").functionCounter());
            assertNotNull(first.find("app.one").gauge());
            assertEquals(1, turboFilters());

            binder(harness, "two");
            harness.reload();
            assertEquals(2, harness.generation());

            MeterRegistry second = harness.context().getBean(MeterRegistry.class);
            assertNotSame(first, second, "the registry is created again in each generation");
            assertNotNull(second.find("jvm.memory.used").gauge());
            assertNotNull(second.find("logback.events").functionCounter());
            assertEquals(1, turboFilters(), "the turbo filter of the stopped generation was removed");

            WeakReference<MeterRegistry> firstRegistry = new WeakReference<>(first);
            first = null;
            ReloadTck.assertRetiredGenerationsCollected(harness);
            awaitCollected(firstRegistry);
        }
        assertEquals(0, turboFilters(), "the turbo filter of the last generation was removed");
    }

    @Test
    void aChangedApplicationBinderRegistersItsMetersInTheNextGeneration() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            binder(harness, "one");
            harness.start();
            MeterRegistry first = harness.context().getBean(MeterRegistry.class);
            assertEquals(1.0, first.get("app.one").gauge().value());

            binder(harness, "two");
            harness.reload();
            assertEquals(2, harness.generation());

            MeterRegistry second = harness.context().getBean(MeterRegistry.class);
            assertEquals(2.0, second.get("app.two").gauge().value(), "the changed binder bound the next registry");
            assertNull(second.find("app.one").gauge(), "the meters of the previous binder are gone");
            first = null;
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    /**
     * An application binder whose gauge is computed by a lambda of the application: a registry that outlived its
     * generation would keep the generation's classes through it.
     */
    private static void binder(ReloadHarness harness, String name) {
        harness.source(BINDER, """
            package example;

            import io.micrometer.core.instrument.Gauge;
            import io.micrometer.core.instrument.MeterRegistry;
            import io.micrometer.core.instrument.binder.MeterBinder;

            @jakarta.inject.Singleton
            public class AppMetrics implements MeterBinder {
                private final double value = "%s".equals("one") ? 1.0 : 2.0;

                @Override
                public void bindTo(MeterRegistry registry) {
                    Gauge.builder("app.%s", this, metrics -> metrics.value).strongReference(true).register(registry);
                }
            }
            """.formatted(name, name));
    }

    private static long turboFilters() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        return loggerContext.getTurboFilterList().stream()
            .filter(filter -> filter.getClass().getName().equals(TURBO_FILTER))
            .count();
    }

    private static void awaitCollected(WeakReference<?> reference) {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertNull(reference.get(), "the registry of the stopped generation is still reachable");
    }
}
