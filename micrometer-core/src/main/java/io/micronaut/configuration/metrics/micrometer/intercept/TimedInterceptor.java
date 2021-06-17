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
import io.micronaut.aop.*;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.reactivex.Flowable;
import io.reactivex.Single;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements support for {@link io.micrometer.core.annotation.Timed} as AOP advice.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Singleton
@RequiresMetrics
@TypeHint(value = {org.HdrHistogram.Histogram.class, org.HdrHistogram.ConcurrentHistogram.class})
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

    /**
     * Default constructor.
     * @param meterRegistry The meter registry
     */
    protected TimedInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final AnnotationValue<TimedSet> timedSet = metadata.getAnnotation(TimedSet.class);
        if (timedSet != null) {
            final List<AnnotationValue<Timed>> timedAnnotations = timedSet.getAnnotations(AnnotationMetadata.VALUE_MEMBER, Timed.class);
            if (!timedAnnotations.isEmpty()) {

                String exceptionClass = "none";
                List<Timer.Sample> syncInvokeSamples = null;
                InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
                try {
                    InterceptedMethod.ResultType resultType = interceptedMethod.resultType();
                    switch (resultType) {
                        case PUBLISHER:
                            Object interceptResult = context.proceed();
                            if (interceptResult == null) {
                                return null;
                            }
                            Object result;
                            AtomicReference<List<Timer.Sample>> reactiveInvokeSample = new AtomicReference<>();
                            if (context.getReturnType().isSingleResult()) {
                                Single<?> single = Publishers.convertPublisher(interceptResult, Single.class);
                                result = single.doOnSubscribe(d -> reactiveInvokeSample.set(initSamples(timedAnnotations)))
                                        .doOnError(throwable -> finalizeSamples(timedAnnotations, throwable.getClass().getSimpleName(), reactiveInvokeSample.get()))
                                        .doOnSuccess(o -> finalizeSamples(timedAnnotations, "none", reactiveInvokeSample.get()));
                            } else {
                                AtomicReference<String> exceptionClassHolder = new AtomicReference<>("none");
                                Flowable<?> flowable = Publishers.convertPublisher(interceptResult, Flowable.class);
                                result = flowable.doOnRequest(n -> reactiveInvokeSample.set(initSamples(timedAnnotations)))
                                        .doOnError(throwable -> exceptionClassHolder.set(throwable.getClass().getSimpleName()))
                                        .doOnComplete(() -> finalizeSamples(timedAnnotations, exceptionClassHolder.get(), reactiveInvokeSample.get()));
                            }
                            return Publishers.convertPublisher(result, context.getReturnType().getType());
                        case COMPLETION_STAGE:
                            List<Timer.Sample> completionStageInvokeSamples = initSamples(timedAnnotations);
                            CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                            CompletionStage<?> completionStageResult = completionStage
                                    .whenComplete((o, throwable) ->
                                            finalizeSamples(
                                                    timedAnnotations, throwable == null ? "none" : throwable.getClass().getSimpleName(),
                                                    completionStageInvokeSamples
                                            )
                                    );

                            return interceptedMethod.handleResult(completionStageResult);
                        case SYNCHRONOUS:
                            syncInvokeSamples = initSamples(timedAnnotations);
                            return context.proceed();
                        default:
                            return interceptedMethod.unsupported();
                    }
                } catch (Exception e) {
                    exceptionClass = e.getClass().getSimpleName();
                    return interceptedMethod.handleException(e);
                } finally {
                    finalizeSamples(timedAnnotations, exceptionClass, syncInvokeSamples);
                }
            }
        }
        return context.proceed();
    }

    private List<Timer.Sample> initSamples(List<AnnotationValue<Timed>> timedAnnotations) {
        List<Timer.Sample> syncInvokeSamples = new ArrayList<>(timedAnnotations.size());
        for (AnnotationValue<Timed> ignored : timedAnnotations) {
            syncInvokeSamples.add(Timer.start(meterRegistry));
        }
        return syncInvokeSamples;
    }

    private void finalizeSamples(List<AnnotationValue<Timed>> timedAnnotations, String exceptionClass, List<Timer.Sample> syncInvokeSamples) {
        if (CollectionUtils.isNotEmpty(syncInvokeSamples) && timedAnnotations.size() == syncInvokeSamples.size()) {
            final Iterator<AnnotationValue<Timed>> i = timedAnnotations.iterator();
            for (Timer.Sample syncInvokeSample : syncInvokeSamples) {
                final AnnotationValue<Timed> timedAnn = i.next();
                final String metricName = timedAnn.stringValue().orElse(DEFAULT_METRIC_NAME);
                stopTimed(metricName, syncInvokeSample, exceptionClass, timedAnn);
            }
        }
    }

    private void stopTimed(String metricName, Timer.Sample sample, String exceptionClass, AnnotationValue<Timed> metadata) {
        try {
            final String description = metadata.stringValue("description").orElse(null);
            final String[] tags = metadata.stringValues("extraTags");
            final double[] percentiles = metadata.get("percentiles", double[].class).orElse(null);
            final boolean histogram = metadata.isTrue("histogram");
            final Timer timer = Timer.builder(metricName)
                    .description(description)
                    .tags(tags)
                    .tags(EXCEPTION_TAG, exceptionClass)
                    .publishPercentileHistogram(histogram)
                    .publishPercentiles(percentiles)
                    .register(meterRegistry);
            sample.stop(timer);
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Error registering timer in the registry", e);
            }
        }
    }

}
