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
import io.micrometer.core.aop.MeterTag;
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
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Inject;
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
    private final @Nullable BeanContext beanContext;
    private final MethodMetricsCache<CountedMethod> countedMethods = new MethodMetricsCache<>();

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @param methodTaggers Additional tag builders
     * @param beanContext The bean context used to look up the resolvers of {@link MeterTag} annotations
     * @since 6.2.0
     */
    @Inject
    public CountedInterceptor(MeterRegistry meterRegistry,
                              ConversionService conversionService,
                              List<AbstractMethodTagger> methodTaggers,
                              BeanContext beanContext) {
        this.meterRegistry = meterRegistry;
        this.conversionService = conversionService;
        this.methodTaggers = Objects.requireNonNullElse(methodTaggers, Collections.emptyList());
        this.beanContext = beanContext;
    }

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @param methodTaggers Additional tag builders
     * @deprecated Use {@link #CountedInterceptor(MeterRegistry, ConversionService, List, BeanContext)} instead.
     * Without the bean context, a {@link MeterTag#resolver()} must have a public no-argument constructor and
     * {@link MeterTag#expression()} only supports {@code #{...}} expressions.
     */
    @Deprecated(since = "6.2.0", forRemoval = true)
    public CountedInterceptor(MeterRegistry meterRegistry, ConversionService conversionService, List<AbstractMethodTagger> methodTaggers) {
        this.meterRegistry = meterRegistry;
        this.conversionService = conversionService;
        this.methodTaggers = Objects.requireNonNullElse(methodTaggers, Collections.emptyList());
        this.beanContext = null;
    }

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        final CountedMethod countedMethod = countedMethod(context);
        if (countedMethod == null || !countedMethod.enabled) {
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
            doCount(context, countedMethod, MethodMetricsSupport.exceptionTag(e), null);
            throw e;
        }
        if (!countedMethod.recordFailuresOnly) {
            doCount(context, countedMethod, null, result);
        }
        return result;
    }

    private @Nullable Object interceptPublisher(MethodInvocationContext<Object, Object> context, CountedMethod countedMethod) {
        final Object result;
        try {
            result = context.proceed();
        } catch (Exception e) {
            doCount(context, countedMethod, MethodMetricsSupport.exceptionTag(e), null);
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
            doCount(context, countedMethod, MethodMetricsSupport.exceptionTag(e), null);
            return interceptedMethod.handleException(e);
        } catch (Error e) {
            // counted as for a synchronous method
            doCount(context, countedMethod, MethodMetricsSupport.exceptionTag(e), null);
            throw e;
        }
        return interceptedMethod.handleResult(completionStage.whenComplete(new CountedInvocation(context, countedMethod)));
    }

    /**
     * @param context The invocation context
     * @return The settings of the invocation, or {@code null} if the {@link MetricOptions#condition()} is false,
     * which is evaluated before the other annotation values
     */
    private @Nullable CountedMethod countedMethod(MethodInvocationContext<Object, Object> context) {
        final CountedMethod cached = countedMethods.get(context.getExecutableMethod());
        if (cached != null) {
            if (cached.hasCondition && !MetricOptionsUtil.evaluateCondition(context)) {
                return null;
            }
            // annotation values containing expressions are evaluated against each invocation
            return cached.dynamic ? resolveCountedMethod(context, cached) : cached;
        }
        if (context.getAnnotationMetadata().isPresent(MetricOptions.class, MetricOptions.MEMBER_CONDITION)
            && !MetricOptionsUtil.evaluateCondition(context)) {
            // resolved and cached by the first invocation that is enabled
            return null;
        }
        final CountedMethod resolved = resolveCountedMethod(context, null);
        // a concurrent first invocation may have cached its own settings: the evaluated values are
        // those of this invocation
        countedMethods.put(context.getExecutableMethod(), resolved);
        return resolved;
    }

    private CountedMethod resolveCountedMethod(MethodInvocationContext<Object, Object> context, @Nullable CountedMethod cached) {
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final AnnotationValue<Counted> counted = metadata.getAnnotation(Counted.class);
        final String metricName = metadata.stringValue(Counted.class).orElse(DEFAULT_METRIC_NAME);
        final String[] extraTags = metadata.stringValues(Counted.class, "extraTags");
        final Tags parsedExtraTags = MethodMetricsSupport.parseTags(extraTags);
        return new CountedMethod(
            InterceptedMethod.of(context, conversionService).resultType(),
            context.getReturnType().isSingleResult(),
            StringUtils.isNotEmpty(metricName),
            metadata.isPresent(MetricOptions.class, MetricOptions.MEMBER_CONDITION),
            counted != null && counted.hasEvaluatedExpressions(),
            metricName,
            metadata.stringValue(Counted.class, "description").orElse(null),
            extraTags,
            parsedExtraTags,
            parsedExtraTags != null
                ? parsedExtraTags.and(EXCEPTION_TAG, MethodMetricsSupport.NONE).and(RESULT_TAG, SUCCESS)
                : null,
            metadata.isTrue(Counted.class, "recordFailuresOnly"),
            MethodMetricsSupport.resolveTaggers(methodTaggers, metadata),
            cached != null ? cached.meterTags : MeterTagPlan.of(context, beanContext)
        );
    }

    private void doCount(MethodInvocationContext<Object, Object> context,
                         CountedMethod countedMethod,
                         @Nullable String exception,
                         @Nullable Object result) {
        try {
            final Counter.Builder builder = Counter.builder(countedMethod.name)
                .description(countedMethod.description);
            if (exception == null && countedMethod.successTags != null && countedMethod.meterTags == null
                && countedMethod.taggers.length == 0) {
                // the common case: the tags are known in advance, no need to merge them for each invocation
                builder.tags(countedMethod.successTags);
            } else {
                builder.tags(MethodMetricsSupport.taggerTags(countedMethod.taggers, context));
                if (countedMethod.parsedExtraTags != null) {
                    builder.tags(countedMethod.parsedExtraTags);
                } else {
                    builder.tags(countedMethod.extraTags);
                }
                if (countedMethod.meterTags != null) {
                    builder.tags(countedMethod.meterTags.tags(context, result));
                }
                builder.tag(EXCEPTION_TAG, exception != null ? exception : MethodMetricsSupport.NONE)
                    .tag(RESULT_TAG, exception != null ? FAILURE : SUCCESS);
            }
            builder.register(meterRegistry)
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
     * @param successTags The parsed extra tags and the {@code exception=none} and {@code result=success} tags,
     *                    i.e. all the tags of a successful invocation of a method without tagger or
     *                    {@code @MeterTag}, or {@code null} if the extra tags are invalid
     * @param recordFailuresOnly Whether only failures are counted
     * @param taggers The taggers that apply to the method
     * @param meterTags The {@link MeterTag} annotations, or {@code null} if the method has none
     */
    @SuppressWarnings("java:S6218") // never compared, the method cache looks entries up by method identity
    private record CountedMethod(InterceptedMethod.ResultType resultType,
                                 boolean singleResult,
                                 boolean enabled,
                                 boolean hasCondition,
                                 boolean dynamic,
                                 String name,
                                 @Nullable String description,
                                 String[] extraTags,
                                 @Nullable Tags parsedExtraTags,
                                 @Nullable Tags successTags,
                                 boolean recordFailuresOnly,
                                 AbstractMethodTagger[] taggers,
                                 @Nullable MeterTagPlan meterTags) {
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

        void onResult(@Nullable Object result, @Nullable Throwable throwable) {
            if (throwable != null) {
                doCount(context, countedMethod, MethodMetricsSupport.asyncExceptionTag(throwable), null);
            } else if (!countedMethod.recordFailuresOnly) {
                doCount(context, countedMethod, null, result);
            }
        }

        @Override
        public void accept(@Nullable Object result, @Nullable Throwable throwable) {
            onResult(result, throwable);
        }

        @Override
        public <T> InstrumentedSubscriber<T> create(CoreSubscriber<? super T> actual, boolean singleResult) {
            return new InstrumentedSubscriber<>(actual, singleResult) {
                @Override
                protected void onStart() {
                    // counted on completion only
                }

                @Override
                protected void onFinish(@Nullable Object value, @Nullable Throwable error, boolean cancelled) {
                    if (!cancelled) {
                        onResult(value, error);
                    }
                }
            };
        }
    }
}
