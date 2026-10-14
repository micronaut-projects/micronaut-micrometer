/*
 * Copyright 2017-2019 original authors
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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.annotation.TimedSet;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micronaut.aop.InterceptPhase;
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
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiConsumer;

import static io.micronaut.core.annotation.AnnotationMetadata.VALUE_MEMBER;

/**
 * Implements support for {@link io.micrometer.core.annotation.Timed} as AOP advice.
 *
 * <p>Synchronous methods are timed from invocation until they return or throw. {@link CompletionStage} methods
 * (including Kotlin suspend functions) are timed until the stage completes. Reactive methods are timed from
 * subscription until the publisher completes, errors or is cancelled, which is recorded with the
 * {@code CancellationException} exception tag. With {@code longTask = true} a {@link LongTaskTimer} is used instead
 * of a {@link Timer}.</p>
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Singleton
@RequiresMetrics
@TypeHint(value = {Histogram.class, ConcurrentHistogram.class})
@InterceptorBean(Timed.class)
public class TimedInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * Default metric name.
     *
     * @since 1.1.0
     */
    public static final String DEFAULT_METRIC_NAME = TimedAspect.DEFAULT_METRIC_NAME;

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = TimedAspect.EXCEPTION_TAG;

    private static final Logger LOGGER = LoggerFactory.getLogger(TimedInterceptor.class);
    private static final TimerSettings[] NO_TIMERS = new TimerSettings[0];

    private final MeterRegistry meterRegistry;
    private final ConversionService conversionService;
    private final List<AbstractMethodTagger> methodTaggers;
    private final Clock clock;
    private final @Nullable BeanContext beanContext;
    private final MethodMetricsCache<TimedMethod> timedMethods = new MethodMetricsCache<>();

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @param methodTaggers Additional tag builders
     * @param beanContext The bean context used to look up the resolvers of {@link MeterTag} annotations
     * @since 6.2.0
     */
    @Inject
    protected TimedInterceptor(MeterRegistry meterRegistry,
                               ConversionService conversionService,
                               List<AbstractMethodTagger> methodTaggers,
                               BeanContext beanContext) {
        this.meterRegistry = meterRegistry;
        this.conversionService = conversionService;
        this.methodTaggers = Objects.requireNonNullElse(methodTaggers, Collections.emptyList());
        this.clock = meterRegistry.config().clock();
        this.beanContext = beanContext;
    }

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @param methodTaggers Additional tag builders
     * @deprecated Use {@link #TimedInterceptor(MeterRegistry, ConversionService, List, BeanContext)} instead. Without
     * the bean context, a {@link MeterTag#resolver()} must have a public no-argument constructor and
     * {@link MeterTag#expression()} only supports {@code #{...}} expressions.
     */
    @Deprecated(since = "6.2.0", forRemoval = true)
    protected TimedInterceptor(MeterRegistry meterRegistry, ConversionService conversionService, List<AbstractMethodTagger> methodTaggers) {
        this.meterRegistry = meterRegistry;
        this.conversionService = conversionService;
        this.methodTaggers = Objects.requireNonNullElse(methodTaggers, Collections.emptyList());
        this.clock = meterRegistry.config().clock();
        this.beanContext = null;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRACE.getPosition();
    }

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        final TimedMethod timedMethod = timedMethod(context);
        if (timedMethod == null || timedMethod.timers.length == 0) {
            return context.proceed();
        }
        return switch (timedMethod.resultType) {
            case SYNCHRONOUS -> interceptSynchronous(context, timedMethod);
            case PUBLISHER -> interceptPublisher(context, timedMethod);
            case COMPLETION_STAGE -> interceptCompletionStage(context, timedMethod);
        };
    }

    private @Nullable Object interceptSynchronous(MethodInvocationContext<Object, Object> context, TimedMethod timedMethod) {
        final long start = clock.monotonicTime();
        final LongTaskTimer.@Nullable Sample[] longTasks = startLongTasks(context, timedMethod);
        String exception = MethodMetricsSupport.NONE;
        Object result = null;
        try {
            result = context.proceed();
            return result;
        } catch (Throwable e) {
            exception = MethodMetricsSupport.exceptionTag(e);
            throw e;
        } finally {
            stop(context, timedMethod, start, longTasks, exception, result);
        }
    }

    private @Nullable Object interceptPublisher(MethodInvocationContext<Object, Object> context, TimedMethod timedMethod) {
        final long start = clock.monotonicTime();
        final Object result;
        try {
            result = context.proceed();
        } catch (Exception e) {
            // nothing was subscribed, so record the synchronous failure of the method itself
            stop(context, timedMethod, start, null, MethodMetricsSupport.exceptionTag(e), null);
            return InterceptedMethod.of(context, conversionService).handleException(e);
        }
        if (result == null) {
            return null;
        }
        final TimedInvocation invocation = new TimedInvocation(context, timedMethod);
        final Publisher<?> publisher = Publishers.convertPublisher(conversionService, result, Publisher.class);
        final Publisher<?> instrumented = timedMethod.singleResult
            ? InstrumentedSubscriber.instrument(Mono.from(publisher), invocation)
            : InstrumentedSubscriber.instrument(Flux.from(publisher), invocation);
        return Publishers.convertPublisher(conversionService, instrumented, context.getReturnType().getType());
    }

    private @Nullable Object interceptCompletionStage(MethodInvocationContext<Object, Object> context, TimedMethod timedMethod) {
        final InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        final TimedInvocation invocation = new TimedInvocation(context, timedMethod);
        invocation.start();
        try {
            final CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
            return interceptedMethod.handleResult(completionStage.whenComplete(invocation));
        } catch (Exception e) {
            invocation.finish(MethodMetricsSupport.exceptionTag(e), null);
            return interceptedMethod.handleException(e);
        } catch (Error e) {
            // the long task sample was started
            invocation.finish(MethodMetricsSupport.exceptionTag(e), null);
            throw e;
        }
    }

    /**
     * @param context The invocation context
     * @return The settings of the invocation, or {@code null} if the {@link MetricOptions#condition()} is false,
     * which is evaluated before the other annotation values
     */
    private @Nullable TimedMethod timedMethod(MethodInvocationContext<Object, Object> context) {
        final TimedMethod cached = timedMethods.get(context.getExecutableMethod());
        if (cached != null) {
            if (cached.hasCondition && !MetricOptionsUtil.evaluateCondition(context)) {
                return null;
            }
            // annotation values containing expressions are evaluated against each invocation
            return cached.dynamic ? resolveTimedMethod(context, cached) : cached;
        }
        if (context.getAnnotationMetadata().isPresent(MetricOptions.class, MetricOptions.MEMBER_CONDITION)
            && !MetricOptionsUtil.evaluateCondition(context)) {
            // resolved and cached by the first invocation that is enabled
            return null;
        }
        final TimedMethod resolved = resolveTimedMethod(context, null);
        // a concurrent first invocation may have cached its own settings: the evaluated values are
        // those of this invocation
        timedMethods.put(context.getExecutableMethod(), resolved);
        return resolved;
    }

    private TimedMethod resolveTimedMethod(MethodInvocationContext<Object, Object> context, @Nullable TimedMethod cached) {
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final AnnotationValue<TimedSet> timedSet = metadata.getAnnotation(TimedSet.class);
        final List<AnnotationValue<Timed>> timedAnnotations = timedSet != null
            ? timedSet.getAnnotations(VALUE_MEMBER, Timed.class)
            : Collections.emptyList();
        final TimerSettings[] timers = timedAnnotations.isEmpty() ? NO_TIMERS : new TimerSettings[timedAnnotations.size()];
        boolean dynamic = false;
        boolean hasLongTask = false;
        for (int i = 0; i < timers.length; i++) {
            final AnnotationValue<Timed> timed = timedAnnotations.get(i);
            final String[] extraTags = timed.stringValues("extraTags");
            final boolean longTask = timed.isTrue("longTask");
            timers[i] = new TimerSettings(
                timed.stringValue().orElse(DEFAULT_METRIC_NAME),
                timed.stringValue("description").orElse(null),
                extraTags,
                MethodMetricsSupport.parseTags(extraTags),
                timed.doubleValues("percentiles"),
                timed.isTrue("histogram"),
                longTask
            );
            dynamic |= timed.hasEvaluatedExpressions();
            hasLongTask |= longTask;
        }
        return new TimedMethod(
            InterceptedMethod.of(context, conversionService).resultType(),
            context.getReturnType().isSingleResult(),
            metadata.isPresent(MetricOptions.class, MetricOptions.MEMBER_CONDITION),
            dynamic,
            hasLongTask,
            timers,
            MethodMetricsSupport.resolveTaggers(methodTaggers, metadata),
            cached != null ? cached.meterTags : MeterTagPlan.of(context, beanContext)
        );
    }

    private LongTaskTimer.@Nullable Sample @Nullable [] startLongTasks(MethodInvocationContext<Object, Object> context, TimedMethod timedMethod) {
        if (!timedMethod.hasLongTask) {
            return null;
        }
        final TimerSettings[] timers = timedMethod.timers;
        final LongTaskTimer.@Nullable Sample[] samples = new LongTaskTimer.Sample[timers.length];
        for (int i = 0; i < timers.length; i++) {
            final TimerSettings timer = timers[i];
            if (timer.longTask) {
                try {
                    final LongTaskTimer.Builder builder = LongTaskTimer.builder(timer.name)
                        .description(timer.description)
                        .tags(MethodMetricsSupport.taggerTags(timedMethod.taggers, context));
                    if (timer.parsedExtraTags != null) {
                        builder.tags(timer.parsedExtraTags);
                    } else {
                        builder.tags(timer.extraTags);
                    }
                    if (timedMethod.meterTags != null) {
                        // the result is not known yet, only the parameter tags apply
                        builder.tags(timedMethod.meterTags.parameterTags(context));
                    }
                    samples[i] = builder.register(meterRegistry).start();
                } catch (Exception e) {
                    LOGGER.error("Error registering long task timer in the registry", e);
                }
            }
        }
        return samples;
    }

    private void stop(MethodInvocationContext<Object, Object> context,
                      TimedMethod timedMethod,
                      long start,
                      LongTaskTimer.@Nullable Sample @Nullable [] longTasks,
                      String exception,
                      @Nullable Object result) {
        final long duration = clock.monotonicTime() - start;
        final TimerSettings[] timers = timedMethod.timers;
        List<Tag> meterTags = null;
        for (int i = 0; i < timers.length; i++) {
            final TimerSettings timer = timers[i];
            if (timer.longTask) {
                final LongTaskTimer.Sample sample = longTasks != null ? longTasks[i] : null;
                if (sample != null) {
                    sample.stop();
                }
            } else {
                if (meterTags == null && timedMethod.meterTags != null) {
                    meterTags = meterTags(context, timedMethod.meterTags, result);
                }
                recordTimer(context, timedMethod, timer, duration, exception, meterTags);
            }
        }
    }

    private void recordTimer(MethodInvocationContext<Object, Object> context,
                             TimedMethod timedMethod,
                             TimerSettings timer,
                             long duration,
                             String exception,
                             @Nullable List<Tag> meterTags) {
        try {
            final Timer.Builder builder = Timer.builder(timer.name)
                .description(timer.description)
                .tags(MethodMetricsSupport.taggerTags(timedMethod.taggers, context));
            if (timer.parsedExtraTags != null) {
                builder.tags(timer.parsedExtraTags);
            } else {
                builder.tags(timer.extraTags);
            }
            if (meterTags != null) {
                builder.tags(meterTags);
            }
            builder.tags(EXCEPTION_TAG, exception)
                .publishPercentileHistogram(timer.histogram)
                .publishPercentiles(timer.percentiles)
                .register(meterRegistry)
                .record(duration, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            LOGGER.error("Error registering timer in the registry", e);
        }
    }

    private static List<Tag> meterTags(MethodInvocationContext<Object, Object> context, MeterTagPlan plan, @Nullable Object result) {
        try {
            return plan.tags(context, result);
        } catch (Exception e) {
            LOGGER.error("Error resolving the @MeterTag values", e);
            return Collections.emptyList();
        }
    }

    /**
     * Settings of a {@link Timed} annotation.
     *
     * @param name The metric name
     * @param description The description
     * @param extraTags The extra tags as declared
     * @param parsedExtraTags The parsed extra tags or {@code null} if they are invalid
     * @param percentiles The percentiles
     * @param histogram Whether to publish a histogram
     * @param longTask Whether to use a long task timer
     */
    @SuppressWarnings("java:S6218") // never compared, the method cache looks entries up by method identity
    private record TimerSettings(String name,
                                 @Nullable String description,
                                 String[] extraTags,
                                 @Nullable Tags parsedExtraTags,
                                 double[] percentiles,
                                 boolean histogram,
                                 boolean longTask) {
    }

    /**
     * Settings of a timed method resolved once from its annotations.
     *
     * @param resultType The result type
     * @param singleResult Whether a reactive result emits at most one value
     * @param hasCondition Whether a {@link MetricOptions#condition()} has to be evaluated for each invocation
     * @param dynamic Whether the annotations contain expressions and have to be resolved for each invocation
     * @param hasLongTask Whether any of the timers is a long task timer
     * @param timers The timers
     * @param taggers The taggers that apply to the method
     * @param meterTags The {@link MeterTag} annotations, or {@code null} if the method has none
     */
    @SuppressWarnings("java:S6218") // never compared, the method cache looks entries up by method identity
    private record TimedMethod(InterceptedMethod.ResultType resultType,
                               boolean singleResult,
                               boolean hasCondition,
                               boolean dynamic,
                               boolean hasLongTask,
                               TimerSettings[] timers,
                               AbstractMethodTagger[] taggers,
                               @Nullable MeterTagPlan meterTags) {
    }

    /**
     * State of an asynchronous invocation, finished exactly once.
     */
    private final class TimedInvocation implements BiConsumer<Object, Throwable>, InstrumentedSubscriber.Factory {

        private static final AtomicIntegerFieldUpdater<TimedInvocation> FINISHED_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(TimedInvocation.class, "finished");

        private final MethodInvocationContext<Object, Object> context;
        private final TimedMethod timedMethod;
        private long start;
        private LongTaskTimer.@Nullable Sample @Nullable [] longTasks;
        private volatile int finished;

        TimedInvocation(MethodInvocationContext<Object, Object> context, TimedMethod timedMethod) {
            this.context = context;
            this.timedMethod = timedMethod;
        }

        void start() {
            start = clock.monotonicTime();
            longTasks = startLongTasks(context, timedMethod);
        }

        void finish(String exception, @Nullable Object result) {
            if (FINISHED_UPDATER.compareAndSet(this, 0, 1)) {
                stop(context, timedMethod, start, longTasks, exception, result);
            }
        }

        @Override
        public void accept(@Nullable Object result, @Nullable Throwable throwable) {
            finish(throwable == null ? MethodMetricsSupport.NONE : MethodMetricsSupport.asyncExceptionTag(throwable), result);
        }

        @Override
        public <T> InstrumentedSubscriber<T> create(CoreSubscriber<? super T> actual, boolean singleResult) {
            return new TimedSubscriber<>(actual, singleResult, context, timedMethod);
        }
    }

    /**
     * Times a single subscription.
     *
     * @param <T> The element type
     */
    private final class TimedSubscriber<T> extends InstrumentedSubscriber<T> {

        private final MethodInvocationContext<Object, Object> context;
        private final TimedMethod timedMethod;
        private long start;
        private LongTaskTimer.@Nullable Sample @Nullable [] longTasks;

        TimedSubscriber(CoreSubscriber<? super T> actual,
                        boolean singleResult,
                        MethodInvocationContext<Object, Object> context,
                        TimedMethod timedMethod) {
            super(actual, singleResult);
            this.context = context;
            this.timedMethod = timedMethod;
        }

        @Override
        protected void onStart() {
            start = clock.monotonicTime();
            longTasks = startLongTasks(context, timedMethod);
        }

        @Override
        protected void onFinish(@Nullable Object value, @Nullable Throwable error, boolean cancelled) {
            final String exception;
            if (error != null) {
                exception = MethodMetricsSupport.asyncExceptionTag(error);
            } else if (cancelled) {
                exception = MethodMetricsSupport.CANCELLED;
            } else {
                exception = MethodMetricsSupport.NONE;
            }
            stop(context, timedMethod, start, longTasks, exception, value);
        }
    }
}
