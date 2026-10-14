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
package io.micronaut.micrometer.test;

import io.micrometer.core.instrument.observation.MeterObservationHandler;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.micrometer.observation.DefaultObservedFactory;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Replaces the {@link ObservationRegistry} of {@code micronaut-micrometer-observation} with a
 * {@link TestObservationRegistry}, so that tests can inject it and assert on the observations
 * with {@code TestObservationRegistryAssert}.
 *
 * <p>The registry is configured with the same handlers, predicates, filters and conventions as
 * the default registry, so observations still produce meters and spans. It keeps every
 * observation context in memory for the lifetime of the application context. Set
 * {@value #ENABLED} to {@code false} to keep the default registry.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Factory
@Requires(classes = {DefaultObservedFactory.class, TestObservationRegistry.class})
@Requires(property = TestObservationRegistryFactory.ENABLED, notEquals = FALSE)
public class TestObservationRegistryFactory {

    /**
     * Whether the observation registry is replaced with a {@link TestObservationRegistry}.
     */
    public static final String ENABLED = "micrometer.observations.test-registry.enabled";

    private static final String TRACING_OBSERVATION_HANDLER = "io.micrometer.tracing.handler.TracingObservationHandler";

    /**
     * Creates the test observation registry.
     *
     * @param observationPredicates  The observation predicates
     * @param observationConventions The global observation conventions
     * @param observationHandlers    The observation handlers
     * @param observationFilters     The observation filters
     * @return The registry
     */
    @Singleton
    @Replaces(bean = ObservationRegistry.class, factory = DefaultObservedFactory.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TestObservationRegistry testObservationRegistry(List<ObservationPredicate> observationPredicates,
                                                           List<GlobalObservationConvention<?>> observationConventions,
                                                           List<ObservationHandler<?>> observationHandlers,
                                                           List<ObservationFilter> observationFilters) {
        TestObservationRegistry registry = TestObservationRegistry.create();
        ObservationRegistry.ObservationConfig config = registry.observationConfig();
        // the same grouping as the default registry: the meter handlers, and the tracing handlers, are
        // each combined so that only the first one that supports an observation handles it
        Map<Class<? extends ObservationHandler>, List<ObservationHandler<?>>> groups = new LinkedHashMap<>();
        groups.put(MeterObservationHandler.class, new ArrayList<>());
        ClassUtils.forName(TRACING_OBSERVATION_HANDLER, TestObservationRegistryFactory.class.getClassLoader())
            .ifPresent(type -> groups.put((Class<? extends ObservationHandler>) type, new ArrayList<>()));
        List<ObservationHandler<?>> ungrouped = new ArrayList<>();
        for (ObservationHandler<?> handler : observationHandlers) {
            groups.entrySet().stream()
                .filter(entry -> entry.getKey().isInstance(handler))
                .findFirst()
                .ifPresentOrElse(entry -> entry.getValue().add(handler), () -> ungrouped.add(handler));
        }
        groups.values().stream()
            .filter(handlers -> !handlers.isEmpty())
            .forEach(handlers -> config.observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler((List) handlers)));
        ungrouped.forEach(config::observationHandler);
        observationPredicates.forEach(config::observationPredicate);
        observationFilters.forEach(config::observationFilter);
        observationConventions.forEach(config::observationConvention);
        return registry;
    }
}
