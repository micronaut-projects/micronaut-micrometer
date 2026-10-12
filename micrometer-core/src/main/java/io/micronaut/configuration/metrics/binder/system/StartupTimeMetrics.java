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
package io.micronaut.configuration.metrics.binder.system;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.reflect.ClassUtils;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Registers the {@code application.started.time} and {@code application.ready.time} gauges.
 *
 * <p>The started time is the JVM uptime when the application context has started, so it includes
 * the time spent before the context was created. The ready time is the JVM uptime when an
 * embedded application, such as the HTTP server, has started (see
 * {@link ApplicationReadyTimeMetrics}). When the application has no embedded application, the
 * ready time is the started time.</p>
 *
 * <p>The bean does not depend on the meter registry, so it does not force the registry to be
 * created at startup. Each gauge is registered as soon as both its time and the registry are
 * known: either when the event is received, or when the registry is created and binds this
 * binder.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Singleton
@RequiresMetrics
@Requires(property = StartupTimeMetrics.ENABLED, notEquals = FALSE)
public class StartupTimeMetrics implements MeterBinder, ApplicationEventListener<StartupEvent> {

    /**
     * Whether startup time metrics are enabled.
     */
    public static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".startup.enabled";

    /**
     * The name of the started time gauge.
     */
    public static final String APPLICATION_STARTED_TIME = "application.started.time";

    /**
     * The name of the ready time gauge.
     */
    public static final String APPLICATION_READY_TIME = "application.ready.time";

    /**
     * The tag holding the main application class.
     */
    public static final String MAIN_APPLICATION_CLASS_TAG = "main.application.class";

    private static final String EMBEDDED_APPLICATION = "io.micronaut.runtime.EmbeddedApplication";
    private static final String STARTED_DESCRIPTION = "Time taken to start the application";
    private static final String READY_DESCRIPTION = "Time taken for the application to be ready to service requests";

    private final BeanContext beanContext;
    private final List<MeterRegistry> registries = new ArrayList<>(1);
    private @Nullable Long startedTime;
    private @Nullable Long readyTime;

    /**
     * @param beanContext The bean context
     */
    public StartupTimeMetrics(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public synchronized void bindTo(MeterRegistry registry) {
        registries.add(registry);
        if (startedTime != null) {
            register(registry, APPLICATION_STARTED_TIME, STARTED_DESCRIPTION, startedTime);
        }
        if (readyTime != null) {
            register(registry, APPLICATION_READY_TIME, READY_DESCRIPTION, readyTime);
        }
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        long uptime = uptimeMillis();
        boolean embedded = hasEmbeddedApplication();
        synchronized (this) {
            if (startedTime == null) {
                startedTime = uptime;
                registries.forEach(registry -> register(registry, APPLICATION_STARTED_TIME, STARTED_DESCRIPTION, uptime));
            }
        }
        if (!embedded) {
            ready(uptime);
        }
    }

    /**
     * Records the time at which the application was ready, the first time it is called.
     *
     * @param uptime The JVM uptime in milliseconds
     */
    synchronized void ready(long uptime) {
        if (readyTime == null) {
            readyTime = uptime;
            registries.forEach(registry -> register(registry, APPLICATION_READY_TIME, READY_DESCRIPTION, uptime));
        }
    }

    /**
     * @return The JVM uptime in milliseconds
     */
    static long uptimeMillis() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }

    private boolean hasEmbeddedApplication() {
        return ClassUtils.forName(EMBEDDED_APPLICATION, beanContext.getClassLoader())
            .map(beanContext::containsBean)
            .orElse(false);
    }

    private static void register(MeterRegistry meterRegistry, String name, String description, long millis) {
        double value = millis;
        TimeGauge.builder(name, () -> value, TimeUnit.MILLISECONDS)
            .description(description)
            .tags(tags())
            .register(meterRegistry);
    }

    private static Tags tags() {
        String mainClass = mainApplicationClass();
        return mainClass == null ? Tags.empty() : Tags.of(Tag.of(MAIN_APPLICATION_CLASS_TAG, mainClass));
    }

    /**
     * Reads the main class from the {@code sun.java.command} system property, which the JVM sets to
     * the main class followed by the program arguments. It is omitted when the application was
     * started from a JAR file or the property is not set, for example in a native image.
     */
    @Nullable
    private static String mainApplicationClass() {
        String command = System.getProperty("sun.java.command");
        if (command == null || command.isBlank()) {
            return null;
        }
        String candidate = command.trim().split("\\s+", 2)[0];
        return isClassName(candidate) ? candidate : null;
    }

    private static boolean isClassName(String candidate) {
        if (candidate.endsWith(".jar")) {
            return false;
        }
        for (String part : candidate.split("\\.", -1)) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return false;
            }
            for (int i = 1; i < part.length(); i++) {
                if (!Character.isJavaIdentifierPart(part.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }
}
