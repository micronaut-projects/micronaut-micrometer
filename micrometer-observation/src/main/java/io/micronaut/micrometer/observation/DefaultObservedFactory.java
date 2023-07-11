/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.micrometer.observation;

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingAwareMeterObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Factory for Micrometer Observation integration. Creates the following beans:
 *
 * <ul>
 *     <li>{@link ObservationRegistry} - Instrumented ObservationRegistry</li>
 *     <li>{@link ObservationHandler} - Observation Handlers for exporting metrics, traces</li>
 *     <li>{@link ObservationFilter} - ObservationFilter that adds common low cardinality key values to all observations</li>
 * </ul>
 */
@Factory
@Internal
public final class DefaultObservedFactory {
    @Singleton
    ObservationRegistry observationRegistry(
        @Nullable List<ObservationPredicate> observationPredicates,
        @Nullable List<GlobalObservationConvention<?>> observationConventions,
        @Nullable List<ObservationHandler<?>> observationHandlers,
        @Nullable List<ObservationFilter> observationFilters
    ) {
        ObservationRegistry observationRegistry = ObservationRegistry.create();

        if (observationHandlers != null) {
            observationHandlers.forEach(observationRegistry.observationConfig()::observationHandler);
        }

        if (observationPredicates != null) {
            observationPredicates.forEach(observationRegistry.observationConfig()::observationPredicate);
        }

        if (observationFilters != null) {
            observationFilters.forEach(observationRegistry.observationConfig()::observationFilter);
        }

        if (observationConventions != null) {
            observationConventions.forEach(observationRegistry.observationConfig()::observationConvention);
        }

        return observationRegistry;
    }

    @Singleton
    @Requires(classes = Tracer.class)
    @Requires(bean = Tracer.class)
    public ObservationHandler<?> defaultTracingObservationHandler(Tracer tracer) {
        return new DefaultTracingObservationHandler(tracer);
    }

    @Singleton
    @Requires(classes = Tracer.class)
    @Requires(beans = { Tracer.class, Propagator.class })
    public ObservationHandler<?> propagatingSenderTracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingSenderTracingObservationHandler<>(tracer, propagator);
    }

    @Singleton
    @Requires(classes = Tracer.class)
    @Requires(beans = { Tracer.class, Propagator.class })
    public ObservationHandler<?> propagatingReceiverTracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingReceiverTracingObservationHandler<>(tracer, propagator);
    }

    @Singleton
    @Requires(classes = MeterRegistry.class)
    @Requires(beans = MeterRegistry.class)
    @Requires(missingBeans = Tracer.class)
    public ObservationHandler<?> defaultMeterObservationHandler(MeterRegistry meterRegistry) {
        return new DefaultMeterObservationHandler(meterRegistry);
    }

    @Singleton
    @Requires(classes = {MeterRegistry.class, Tracer.class})
    @Requires(beans = {MeterRegistry.class, Tracer.class})
    public ObservationHandler<?> tracingAwareMeterObservationHandler(MeterRegistry meterRegistry, Tracer tracer) {
        DefaultMeterObservationHandler delegate = new DefaultMeterObservationHandler(meterRegistry);
        return new TracingAwareMeterObservationHandler<>(delegate, tracer);
    }

    @Singleton
    public static ObservationFilter commonKeyValuesFilter(ObservationProperties properties) {
        if (properties.commonKeyValue() == null || properties.commonKeyValue().isEmpty()) {
            return context -> context;
        }
        KeyValues keyValues = KeyValues.of(properties.commonKeyValue().entrySet(), Map.Entry::getKey, Map.Entry::getValue);
        return context -> context.addLowCardinalityKeyValues(keyValues);
    }

}
