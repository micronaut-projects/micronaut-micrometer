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
import io.micrometer.core.instrument.observation.MeterObservationHandler;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingAwareMeterObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * {@code @Order} value of {@link #defaultTracingObservationHandler(Tracer)}.
     */
    public static final int DEFAULT_TRACING_OBSERVATION_HANDLER_ORDER = Ordered.LOWEST_PRECEDENCE - 1000;

    /**
     * {@code @Order} value of
     * {@link #propagatingReceiverTracingObservationHandler(Tracer, Propagator)}.
     */
    public static final int RECEIVER_TRACING_OBSERVATION_HANDLER_ORDER = 1000;

    /**
     * {@code @Order} value of
     * {@link #propagatingSenderTracingObservationHandler(Tracer, Propagator)}.
     */
    public static final int SENDER_TRACING_OBSERVATION_HANDLER_ORDER = 2000;

    @Singleton
    ObservationRegistry observationRegistry(
        List<ObservationPredicate> observationPredicates,
        List<GlobalObservationConvention<?>> observationConventions,
        List<ObservationHandler<?>> observationHandlers,
        List<ObservationFilter> observationFilters,
        ObservationHandlerGrouping observationHandlerGrouping
    ) {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationHandlerGrouping.apply(observationHandlers, observationRegistry.observationConfig());
        observationPredicates.forEach(observationRegistry.observationConfig()::observationPredicate);
        observationFilters.forEach(observationRegistry.observationConfig()::observationFilter);
        observationConventions.forEach(observationRegistry.observationConfig()::observationConvention);
        return observationRegistry;
    }

    @Singleton
    @SuppressWarnings("java:S1452")
    @Requires(classes = Tracer.class)
    @Requires(bean = Tracer.class)
    @Order(DEFAULT_TRACING_OBSERVATION_HANDLER_ORDER)
    public ObservationHandler<?> defaultTracingObservationHandler(Tracer tracer) {
        return new DefaultTracingObservationHandler(tracer);
    }

    @Singleton
    @SuppressWarnings("java:S1452")
    @Requires(classes = Tracer.class)
    @Requires(beans = { Tracer.class, Propagator.class })
    @Order(SENDER_TRACING_OBSERVATION_HANDLER_ORDER)
    public ObservationHandler<?> propagatingSenderTracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingSenderTracingObservationHandler<>(tracer, propagator);
    }

    @Singleton
    @SuppressWarnings("java:S1452")
    @Requires(classes = Tracer.class)
    @Requires(beans = { Tracer.class, Propagator.class })
    @Order(RECEIVER_TRACING_OBSERVATION_HANDLER_ORDER)
    public ObservationHandler<?> propagatingReceiverTracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingReceiverTracingObservationHandler<>(tracer, propagator);
    }

    @Singleton
    @SuppressWarnings("java:S1452")
    @Requires(classes = MeterRegistry.class)
    @Requires(beans = MeterRegistry.class)
    @Requires(missingBeans = Tracer.class)
    public ObservationHandler<?> defaultMeterObservationHandler(MeterRegistry meterRegistry) {
        return new DefaultMeterObservationHandler(meterRegistry);
    }

    @Singleton
    @SuppressWarnings("java:S1452")
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

    @Singleton
    ObservationHandlerGrouping observationHandlerGroupingMetricsWithTracer(List<ObservationHandlerGroupingClass> observationHandlerGroupingClasses) {
        return new ObservationHandlerGrouping(observationHandlerGroupingClasses.stream().map(ObservationHandlerGroupingClass::handler).collect(Collectors.toList()));
    }

    @Singleton
    @Requires(classes = {MeterObservationHandler.class})
    ObservationHandlerGroupingClass observationHandlerGroupingClassMeter() {
        return new ObservationHandlerGroupingClass(MeterObservationHandler.class);
    }

    @Singleton
    @Requires(classes = {TracingObservationHandler.class})
    ObservationHandlerGroupingClass observationHandlerGroupingClassMeterTracer() {
        return new ObservationHandlerGroupingClass(TracingObservationHandler.class);
    }

}
