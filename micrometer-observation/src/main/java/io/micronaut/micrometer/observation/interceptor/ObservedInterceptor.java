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
package io.micronaut.micrometer.observation.interceptor;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * Implements observation logic for {@link Observed} annotation using the Micrometer Observation API.
 *
 * <p>Synchronous methods are observed until they return or throw and {@link CompletionStage} methods until the
 * stage completes. Reactive methods are observed for each subscription, from subscription until the publisher
 * completes, errors or is cancelled. The observation is added to the Reactor context of the subscription.</p>
 */
@Internal
@Singleton
@Requires(beans = ObservationRegistry.class)
@InterceptorBean(Observed.class)
final class ObservedInterceptor implements MethodInterceptor<Object, Object> {

    private static final String DEFAULT_NAME = "method.observed";
    private static final int MAX_CACHED_METHODS = 10_000;

    private final ObservationRegistry observationRegistry;
    private final ConversionService conversionService;
    private volatile Map<ExecutableMethod<?, ?>, ObservedMethod> observedMethods = new IdentityHashMap<>();

    public ObservedInterceptor(ObservationRegistry observationRegistry, ConversionService conversionService) {
        this.observationRegistry = observationRegistry;
        this.conversionService = conversionService;
    }

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        final ObservedMethod observedMethod = observedMethod(context);
        if (observedMethod == null) {
            return context.proceed();
        }
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().propagate()) {
            return switch (observedMethod.resultType) {
                case SYNCHRONOUS -> interceptSynchronous(context, observedMethod);
                case PUBLISHER -> interceptPublisher(context, observedMethod);
                case COMPLETION_STAGE -> interceptCompletionStage(context, observedMethod);
            };
        }
    }

    private @Nullable Object interceptSynchronous(MethodInvocationContext<Object, Object> context, ObservedMethod observedMethod) {
        final Observation observation = observedMethod.start(observationRegistry);
        try (Observation.Scope ignore = observation.openScope()) {
            return context.proceed();
        } catch (Throwable e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private @Nullable Object interceptPublisher(MethodInvocationContext<Object, Object> context, ObservedMethod observedMethod) {
        final Object result;
        try {
            // the method only assembles the publisher, the observation starts when it is subscribed
            result = context.proceed();
        } catch (Exception e) {
            final Observation observation = observedMethod.start(observationRegistry);
            observation.error(e);
            observation.stop();
            return InterceptedMethod.of(context, conversionService).handleException(e);
        }
        if (result == null) {
            return null;
        }
        final InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        final Publisher<?> publisher = Publishers.convertPublisher(conversionService, result, Publisher.class);
        final Publisher<?> observed = observedMethod.singleResult
            ? ObservedSubscriber.observe(Mono.from(publisher), observedMethod, observationRegistry)
            : ObservedSubscriber.observe(Flux.from(publisher), observedMethod, observationRegistry);
        return interceptedMethod.handleResult(observed);
    }

    private @Nullable Object interceptCompletionStage(MethodInvocationContext<Object, Object> context, ObservedMethod observedMethod) {
        final InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        final Observation observation = observedMethod.start(observationRegistry);
        final CompletionStage<?> completionStage;
        try (Observation.Scope ignore = observation.openScope()) {
            completionStage = interceptedMethod.interceptResultAsCompletionStage();
        } catch (Exception e) {
            observation.error(e);
            observation.stop();
            return interceptedMethod.handleException(e);
        }
        return interceptedMethod.handleResult(completionStage.whenComplete((o, throwable) -> {
            if (throwable != null) {
                observation.error(unwrap(throwable));
            }
            observation.stop();
        }));
    }

    private @Nullable ObservedMethod observedMethod(MethodInvocationContext<Object, Object> context) {
        final ExecutableMethod<Object, Object> method = context.getExecutableMethod();
        final ObservedMethod cached = observedMethods.get(method);
        if (cached != null) {
            // annotation values containing expressions are evaluated against each invocation
            return cached.dynamic ? resolveObservedMethod(context) : cached;
        }
        final ObservedMethod resolved = resolveObservedMethod(context);
        return resolved != null ? cache(method, resolved) : null;
    }

    private synchronized ObservedMethod cache(ExecutableMethod<?, ?> method, ObservedMethod observedMethod) {
        final Map<ExecutableMethod<?, ?>, ObservedMethod> current = observedMethods;
        final ObservedMethod existing = current.get(method);
        if (existing != null) {
            return existing;
        }
        if (current.size() < MAX_CACHED_METHODS) {
            final Map<ExecutableMethod<?, ?>, ObservedMethod> copy = new IdentityHashMap<>(current);
            copy.put(method, observedMethod);
            observedMethods = copy;
        }
        return observedMethod;
    }

    private @Nullable ObservedMethod resolveObservedMethod(MethodInvocationContext<Object, Object> context) {
        final AnnotationValue<Observed> observed = context.getAnnotation(Observed.class);
        if (observed == null) {
            return null;
        }
        final String className = context.getDeclaringType().getSimpleName();
        final String methodName = context.getMethodName();
        return new ObservedMethod(
            InterceptedMethod.of(context, conversionService).resultType(),
            context.getReturnType().isSingleResult(),
            observed.hasEvaluatedExpressions(),
            observed.stringValue("name").orElse(DEFAULT_NAME),
            observed.stringValue("contextualName").orElse(className + "#" + methodName),
            KeyValues.of("class", className, "method", methodName)
                .and(KeyValues.of(observed.stringValues("lowCardinalityKeyValues")))
        );
    }

    /**
     * @param throwable The exception
     * @return The cause of the {@link CompletionException} and {@link ExecutionException} wrappers
     */
    static Throwable unwrap(Throwable throwable) {
        Throwable t = throwable;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    /**
     * Settings of an observed method resolved once from its annotation.
     *
     * @param resultType The result type
     * @param singleResult Whether a reactive result emits at most one value
     * @param dynamic Whether the annotation contains expressions and has to be resolved for each invocation
     * @param name The observation name
     * @param contextualName The contextual name
     * @param lowCardinalityKeyValues The low cardinality key values
     */
    record ObservedMethod(InterceptedMethod.ResultType resultType,
                          boolean singleResult,
                          boolean dynamic,
                          String name,
                          String contextualName,
                          KeyValues lowCardinalityKeyValues) {

        /**
         * Creates and starts the observation.
         *
         * @param registry The registry
         * @return The started observation
         */
        Observation start(ObservationRegistry registry) {
            return Observation.createNotStarted(name, registry)
                .contextualName(contextualName)
                .lowCardinalityKeyValues(lowCardinalityKeyValues)
                .start();
        }
    }
}
