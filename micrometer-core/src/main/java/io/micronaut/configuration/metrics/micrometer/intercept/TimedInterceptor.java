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
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger;
import io.micronaut.configuration.metrics.annotation.MetricOptions;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.core.annotation.AnnotationMetadata.VALUE_MEMBER;

/**
 * Implements support for {@link io.micrometer.core.annotation.Timed} as AOP advice.
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

    private final MeterRegistry meterRegistry;
    private final ConversionService conversionService;
    private final List<AbstractMethodTagger> methodTaggers;

    /**
     * @param meterRegistry The meter registry
     * @deprecated Pass conversion service in new constructor
     */
    @Deprecated
    protected TimedInterceptor(MeterRegistry meterRegistry) {
        this(meterRegistry, ConversionService.SHARED, Collections.emptyList());
    }

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @deprecated Pass list of AbstractMethodTaggers in new constructor
     */
    @Deprecated
    protected TimedInterceptor(MeterRegistry meterRegistry, ConversionService conversionService) {
        this(meterRegistry, conversionService, Collections.emptyList());
    }

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @param methodTaggers Additional tag builders
     */
    @Inject
    protected TimedInterceptor(MeterRegistry meterRegistry, ConversionService conversionService, List<AbstractMethodTagger> methodTaggers) {
        this.meterRegistry = meterRegistry;
        this.conversionService = conversionService;
        this.methodTaggers = Objects.requireNonNullElse(methodTaggers, Collections.emptyList());
    }

    @Override
    @SuppressWarnings("java:S3776") // performance
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final AnnotationValue<TimedSet> timedSet = metadata.getAnnotation(TimedSet.class);
        if (timedSet != null) {
            final List<AnnotationValue<Timed>> timedAnnotations = timedSet.getAnnotations(VALUE_MEMBER, Timed.class);
            if (!timedAnnotations.isEmpty()) {

                String exceptionClass = "none";
                List<Timer.Sample> syncInvokeSamples = null;
                InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
                try {
                    InterceptedMethod.ResultType resultType = interceptedMethod.resultType();
                    switch (resultType) {
                        case PUBLISHER -> {
                            Object interceptResult = context.proceed();
                            if (interceptResult == null) {
                                return null;
                            }
                            Object result;
                            AtomicReference<List<Timer.Sample>> reactiveInvokeSample = new AtomicReference<>();
                            if (context.getReturnType().isSingleResult()) {
                                Mono<?> single = Mono.from(Publishers.convertPublisher(conversionService, interceptResult, Publisher.class));
                                result = single.doOnSubscribe(d -> reactiveInvokeSample.set(initSamples(timedAnnotations)))
                                    .doOnError(throwable -> finalizeSamples(timedAnnotations, throwable.getClass().getSimpleName(), reactiveInvokeSample.get(), context))
                                    .doOnSuccess(o -> finalizeSamples(timedAnnotations, "none", reactiveInvokeSample.get(), context));
                            } else {
                                AtomicReference<String> exceptionClassHolder = new AtomicReference<>("none");
                                Flux<?> flowable = Flux.from(Publishers.convertPublisher(conversionService, interceptResult, Publisher.class));
                                result = flowable.doOnRequest(n -> reactiveInvokeSample.set(initSamples(timedAnnotations)))
                                    .doOnError(throwable -> exceptionClassHolder.set(throwable.getClass().getSimpleName()))
                                    .doOnComplete(() -> finalizeSamples(timedAnnotations, exceptionClassHolder.get(), reactiveInvokeSample.get(), context));
                            }
                            return Publishers.convertPublisher(conversionService, result, context.getReturnType().getType());
                        }
                        case COMPLETION_STAGE -> {
                            List<Timer.Sample> completionStageInvokeSamples = initSamples(timedAnnotations);
                            CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                            CompletionStage<?> completionStageResult = completionStage
                                .whenComplete((o, throwable) ->
                                    finalizeSamples(
                                        timedAnnotations, throwable == null ? "none" : throwable.getClass().getSimpleName(),
                                        completionStageInvokeSamples,
                                        context
                                    )
                                );
                            return interceptedMethod.handleResult(completionStageResult);
                        }
                        case SYNCHRONOUS -> {
                            syncInvokeSamples = initSamples(timedAnnotations);
                            return context.proceed();
                        }
                        default -> {
                            return interceptedMethod.unsupported();
                        }
                    }
                } catch (Exception e) {
                    exceptionClass = e.getClass().getSimpleName();
                    return interceptedMethod.handleException(e);
                } finally {
                    finalizeSamples(timedAnnotations, exceptionClass, syncInvokeSamples != null ? syncInvokeSamples : Collections.emptyList(), context);
                }
            }
        }
        return context.proceed();
    }

    @SuppressWarnings("java:S1481")
    private List<Timer.Sample> initSamples(List<AnnotationValue<Timed>> timedAnnotations) {
        List<Timer.Sample> syncInvokeSamples = new ArrayList<>(timedAnnotations.size());
        for (AnnotationValue<Timed> ignored : timedAnnotations) {
            syncInvokeSamples.add(Timer.start(meterRegistry));
        }
        return syncInvokeSamples;
    }

    private void finalizeSamples(List<AnnotationValue<Timed>> timedAnnotations,
                                 String exceptionClass,
                                 List<Timer.Sample> syncInvokeSamples,
                                 MethodInvocationContext<Object, Object> context) {
        if (CollectionUtils.isNotEmpty(syncInvokeSamples) && timedAnnotations.size() == syncInvokeSamples.size()) {
            final Iterator<AnnotationValue<Timed>> i = timedAnnotations.iterator();
            for (Timer.Sample syncInvokeSample : syncInvokeSamples) {
                final AnnotationValue<Timed> timedAnn = i.next();
                final String metricName = timedAnn.stringValue().orElse(DEFAULT_METRIC_NAME);
                stopTimed(metricName, syncInvokeSample, exceptionClass, timedAnn, context);
            }
        }
    }

    private void stopTimed(String metricName, Timer.Sample sample,
                           String exceptionClass, AnnotationValue<Timed> metadata,
                           MethodInvocationContext<Object, Object> context) {
        try {
            final String description = metadata.stringValue("description").orElse(null);
            final String[] tags = metadata.stringValues("extraTags");
            final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
            final List<Class<? extends AbstractMethodTagger>> taggers = Arrays.asList(annotationMetadata.classValues(MetricOptions.class, "taggers"));
            final boolean filter = annotationMetadata.booleanValue(MetricOptions.class, "filterTaggers").orElse(false);
            final double[] percentiles = metadata.doubleValues("percentiles");
            final boolean histogram = metadata.isTrue("histogram");
            final Timer timer = Timer.builder(metricName)
                    .description(description)
                    .tags(
                        methodTaggers.isEmpty() ? Collections.emptyList() :
                            methodTaggers
                            .stream()
                                .sorted(OrderUtil.ORDERED_COMPARATOR)
                                .filter(t -> !filter || taggers.contains(t.getClass()))
                                .flatMap(b -> b.getTags(context).stream())
                            .toList()
                    )
                    .tags(tags)
                    .tags(EXCEPTION_TAG, exceptionClass)
                    .publishPercentileHistogram(histogram)
                    .publishPercentiles(percentiles)
                    .register(meterRegistry);
            sample.stop(timer);
        } catch (Exception e) {
            LOGGER.error("Error registering timer in the registry", e);
        }
    }
}
