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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletionStage;

/**
 * Implements observation logic for {@link Observed} annotation using the Micrometer Observation API.
 */
@Internal
@Singleton
@Requires(beans = ObservationRegistry.class)
@InterceptorBean(Observed.class)
final class ObservedInterceptor implements MethodInterceptor<Object, Object> {

    private final ObservationRegistry observationRegistry;
    private final ConversionService conversionService;

    public ObservedInterceptor(ObservationRegistry observationRegistry, ConversionService conversionService) {
        this.observationRegistry = observationRegistry;
        this.conversionService = conversionService;
    }

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        AnnotationValue<Observed> observed = context.getAnnotation(Observed.class);
        boolean isObserved = observed != null;
        if (!isObserved) {
            return context.proceed();
        }
        // must be new
        // don't create a nested span if you're not supposed to.
        String name = observed.stringValue("name").orElse("method.observed");

        String contextualName = observed.stringValue("contextualName").orElse(context.getDeclaringType().getSimpleName() + "#" + context.getMethodName());
        String[] lowCardinalityKeyValues = observed.stringValues("lowCardinalityKeyValues");

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);

        Observation observation = Observation.createNotStarted(name, observationRegistry)
            .contextualName(contextualName)
            .lowCardinalityKeyValue("class", context.getDeclaringType().getSimpleName())
            .lowCardinalityKeyValue("method", context.getMethodName())
            .lowCardinalityKeyValues(KeyValues.of(lowCardinalityKeyValues));

        observation.start();

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().propagate()) {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    return observation.scoped(() -> interceptedMethod.handleResult(
                        Flux.from(interceptedMethod.interceptResultAsPublisher())
                            .doOnNext(value -> observation.stop())
                            .doOnComplete(observation::stop)
                            .doOnError(t -> {
                                observation.error(t);
                                observation.stop();
                            })
                    ));
                }
                case COMPLETION_STAGE -> {
                    CompletionStage<?> completionStage = observation.scoped(() -> interceptedMethod.interceptResultAsCompletionStage());
                    completionStage = completionStage.whenComplete((o, throwable) -> {
                        if (throwable != null) {
                            observation.error(throwable);
                        }
                        observation.stop();
                    });
                    return interceptedMethod.handleResult(completionStage);
                }
                case SYNCHRONOUS -> {
                    Object response = observation.scoped(() -> context.proceed());
                    observation.stop();
                    return response;
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            observation.error(e);
            observation.stop();
            return interceptedMethod.handleException(e);
        }
    }
}
