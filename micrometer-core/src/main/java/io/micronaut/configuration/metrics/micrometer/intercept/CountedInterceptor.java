/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.configuration.metrics.micrometer.intercept;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger;
import io.micronaut.configuration.metrics.annotation.MetricOptions;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.configuration.metrics.util.MetricOptionsUtil;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import static io.micrometer.core.aop.TimedAspect.EXCEPTION_TAG;

/**
 * Implements support for {@link io.micrometer.core.annotation.Counted} as AOP advice.
 *
 * <p>{@link CompletionStage} methods (including Kotlin suspend functions) are counted when the stage completes and
 * reactive methods when the publisher completes or errors, once per subscription. A cancelled subscription is not
 * counted. {@link Counted#recordFailuresOnly()} is honoured for every result type.</p>
 *
 * @author graemerocher
 * @since 1.1.0
 */
@RequiresMetrics
@InterceptorBean(Counted.class)
public class CountedInterceptor implements MethodInterceptor<Object, Object> {

    public static final String DEFAULT_METRIC_NAME = "method.counted";
    public static final String RESULT_TAG = "result";

    private static final Logger LOGGER = LoggerFactory.getLogger(CountedInterceptor.class);
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry meterRegistry;
    private final ConversionService conversionService;
    private final List<AbstractMethodTagger> methodTaggers;
    private final MethodMetricsCache<CountedMethod> countedMethods = new MethodMetricsCache<>();

    public CountedInterceptor(MeterRegistry meterRegistry, ConversionService conversionService, List<AbstractMethodTagger> methodTaggers) {
        this.meterRegistry = meterRegistry;
        this.conversionService = conversionService;
        this.methodTaggers = Objects.requireNonNullElse(methodTaggers, Collections.emptyList());
    }

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        final CountedMethod countedMethod = countedMethod(context);
        if (!countedMethod.enabled || (countedMethod.hasCondition && !MetricOptionsUtil.evaluateCondition(context))) {
            return context.proceed();
        }
        return switch (countedMethod.resultType) {
            case SYNCHRONOUS -> interceptSynchronous(context, countedMethod);
            case PUBLISHER -> interceptPublisher(context, countedMethod);
            case COMPLETION_STAGE -> interceptCompletionStage(context, countedMethod);
        };
    }

    private @Nullable Object interceptSynchronous(MethodInvocationContext<Object, Object> context, CountedMethod countedMethod) {
        final Object result;
        try {
            result = context.proceed();
        } catch (Throwable e) {
            doCount(context, countedMethod, MethodMetricsSupport.exceptionTag(e));
            throw e;
        }
        if (!countedMethod.recordFailuresOnly) {
            doCount(context, countedMethod, null);
        }
        return result;
    }

    private @Nullable Object interceptPublisher(MethodInvocationContext<Object, Object> context, CountedMethod countedMethod) {
        final Object result;
        try {
            result = context.proceed();
        } catch (Exception e) {
            doCount(context, countedMethod, MethodMetricsSupport.exceptionTag(e));
            return InterceptedMethod.of(context, conversionService).handleException(e);
        }
        if (result == null) {
            return null;
        }
        final CountedInvocation invocation = new CountedInvocation(context, countedMethod);
        final Publisher<?> publisher = Publishers.convertPublisher(conversionService, result, Publisher.class);
        final Publisher<?> instrumented = countedMethod.singleResult
            ? InstrumentedSubscriber.instrument(Mono.from(publisher), invocation)
            : InstrumentedSubscriber.instrument(Flux.from(publisher), invocation);
        return Publishers.convertPublisher(conversionService, instrumented, context.getReturnType().getType());
    }

    private @Nullable Object interceptCompletionStage(MethodInvocationContext<Object, Object> context, CountedMethod countedMethod) {
        final InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        final CompletionStage<?> completionStage;
        try {
            completionStage = interceptedMethod.interceptResultAsCompletionStage();
        } catch (Exception e) {
            doCount(context, countedMethod, MethodMetricsSupport.exceptionTag(e));
            return interceptedMethod.handleException(e);
        }
        return interceptedMethod.handleResult(completionStage.whenComplete(new CountedInvocation(context, countedMethod)));
    }

    private CountedMethod countedMethod(MethodInvocationContext<Object, Object> context) {
        final CountedMethod cached = countedMethods.get(context.getExecutableMethod());
        if (cached != null) {
            // annotation values containing expressions are evaluated against each invocation
            return cached.dynamic ? resolveCountedMethod(context) : cached;
        }
        return countedMethods.put(context.getExecutableMethod(), resolveCountedMethod(context));
    }

    private CountedMethod resolveCountedMethod(MethodInvocationContext<Object, Object> context) {
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final AnnotationValue<Counted> counted = metadata.getAnnotation(Counted.class);
        final String metricName = metadata.stringValue(Counted.class).orElse(DEFAULT_METRIC_NAME);
        final String[] extraTags = metadata.stringValues(Counted.class, "extraTags");
        return new CountedMethod(
            InterceptedMethod.of(context, conversionService).resultType(),
            context.getReturnType().isSingleResult(),
            StringUtils.isNotEmpty(metricName),
            metadata.isPresent(MetricOptions.class, MetricOptions.MEMBER_CONDITION),
            counted != null && counted.hasEvaluatedExpressions(),
            metricName,
            metadata.stringValue(Counted.class, "description").orElse(null),
            extraTags,
            MethodMetricsSupport.parseTags(extraTags),
            metadata.isTrue(Counted.class, "recordFailuresOnly"),
            MethodMetricsSupport.resolveTaggers(methodTaggers, metadata)
        );
    }

    private void doCount(MethodInvocationContext<Object, Object> context, CountedMethod countedMethod, @Nullable String exception) {
        try {
            final Counter.Builder builder = Counter.builder(countedMethod.name)
                .tags(MethodMetricsSupport.taggerTags(countedMethod.taggers, context));
            if (countedMethod.parsedExtraTags != null) {
                builder.tags(countedMethod.parsedExtraTags);
            } else {
                builder.tags(countedMethod.extraTags);
            }
            builder.description(countedMethod.description)
                .tag(EXCEPTION_TAG, exception != null ? exception : MethodMetricsSupport.NONE)
                .tag(RESULT_TAG, exception != null ? FAILURE : SUCCESS)
                .register(meterRegistry)
                .increment();
        } catch (Exception e) {
            LOGGER.error("Error registering counter in the registry", e);
        }
    }

    /**
     * Settings of a counted method resolved once from its annotations.
     *
     * @param resultType The result type
     * @param singleResult Whether a reactive result emits at most one value
     * @param enabled Whether the method is counted
     * @param hasCondition Whether a {@link MetricOptions#condition()} has to be evaluated for each invocation
     * @param dynamic Whether the annotation contains expressions and has to be resolved for each invocation
     * @param name The metric name
     * @param description The description
     * @param extraTags The extra tags as declared
     * @param parsedExtraTags The parsed extra tags or {@code null} if they are invalid
     * @param recordFailuresOnly Whether only failures are counted
     * @param taggers The taggers that apply to the method
     */
    private record CountedMethod(InterceptedMethod.ResultType resultType,
                                 boolean singleResult,
                                 boolean enabled,
                                 boolean hasCondition,
                                 boolean dynamic,
                                 String name,
                                 @Nullable String description,
                                 String[] extraTags,
                                 @Nullable Tags parsedExtraTags,
                                 boolean recordFailuresOnly,
                                 AbstractMethodTagger[] taggers) {
    }

    /**
     * Counts the outcome of an asynchronous invocation.
     */
    private final class CountedInvocation implements BiConsumer<Object, Throwable>, InstrumentedSubscriber.Factory {

        private final MethodInvocationContext<Object, Object> context;
        private final CountedMethod countedMethod;

        CountedInvocation(MethodInvocationContext<Object, Object> context, CountedMethod countedMethod) {
            this.context = context;
            this.countedMethod = countedMethod;
        }

        void onResult(@Nullable Throwable throwable) {
            if (throwable != null) {
                doCount(context, countedMethod, MethodMetricsSupport.asyncExceptionTag(throwable));
            } else if (!countedMethod.recordFailuresOnly) {
                doCount(context, countedMethod, null);
            }
        }

        @Override
        public void accept(Object result, @Nullable Throwable throwable) {
            onResult(throwable);
        }

        @Override
        public <T> InstrumentedSubscriber<T> create(CoreSubscriber<? super T> actual, boolean singleResult) {
            return new InstrumentedSubscriber<>(actual, singleResult) {
                @Override
                protected void onStart() {
                    // counted on completion only
                }

                @Override
                protected void onFinish(@Nullable Throwable error, boolean cancelled) {
                    if (!cancelled) {
                        onResult(error);
                    }
                }
            };
        }
    }
}
