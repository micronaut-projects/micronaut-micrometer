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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

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
public class TimedInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * Default metric name.
     *
     * @since 1.1.0
     */
    public static final String DEFAULT_METRIC_NAME = "method.timed";

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

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
        final String metricName = metadata.getValue(Timed.class, String.class).orElse(DEFAULT_METRIC_NAME);
        if (StringUtils.isNotEmpty(metricName)) {
            String exceptionClass = "none";
            Timer.Sample syncInvokeSample = Timer.start(meterRegistry);
            InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
            try {
                InterceptedMethod.ResultType resultType = interceptedMethod.resultType();
                switch (resultType) {
                    case PUBLISHER:
                        Object interceptResult = context.proceed();
                        if (interceptResult == null) {
                            return null;
                        }
                        syncInvokeSample = null; // Register subscribe -> completed after sync call succeeds
                        Object result;
                        AtomicReference<Timer.Sample> reactiveInvokeSample = new AtomicReference<>();
                        if (Publishers.isSingle(interceptResult.getClass())) {
                            Single<?> single = Publishers.convertPublisher(interceptResult, Single.class);
                            result = single.doOnSubscribe(d -> reactiveInvokeSample.set(Timer.start(meterRegistry)))
                                    .doOnError(throwable -> stopTimed(metricName, reactiveInvokeSample.get(), throwable.getClass().getSimpleName(), metadata))
                                    .doOnSuccess(o -> stopTimed(metricName, reactiveInvokeSample.get(), "none", metadata));
                        } else {
                            AtomicReference<String> exceptionClassHolder = new AtomicReference<>("none");
                            Flowable<?> flowable = Publishers.convertPublisher(interceptResult, Flowable.class);
                            result = flowable.doOnRequest(n -> reactiveInvokeSample.set(Timer.start(meterRegistry)))
                                    .doOnError(throwable -> exceptionClassHolder.set(throwable.getClass().getSimpleName()))
                                    .doOnComplete(() -> {
                                        final Timer.Sample s = reactiveInvokeSample.get();
                                        if (s != null) {
                                            stopTimed(metricName, s, exceptionClassHolder.get(), metadata);
                                        }
                                    });
                        }
                        return Publishers.convertPublisher(result, context.getReturnType().getType());
                    case COMPLETION_STAGE:
                        syncInvokeSample = Timer.start(meterRegistry);
                        CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                        Timer.Sample completionStageInvokeSample = syncInvokeSample;
                        syncInvokeSample = null; // Register after the stage is complete instead of block's finally
                        CompletionStage<?> completionStageResult = completionStage
                                .whenComplete((o, throwable) -> stopTimed(
                                    metricName,
                                    completionStageInvokeSample,
                                    throwable == null ? "none" : throwable.getClass().getSimpleName(),
                                    metadata));

                        return interceptedMethod.handleResult(completionStageResult);
                    case SYNCHRONOUS:
                        syncInvokeSample = Timer.start(meterRegistry);
                        return context.proceed();
                    default:
                        return interceptedMethod.unsupported();
                }
            } catch (Exception e) {
                exceptionClass = e.getClass().getSimpleName();
                return interceptedMethod.handleException(e);
            } finally {
                if (syncInvokeSample != null) {
                    stopTimed(metricName, syncInvokeSample, exceptionClass, metadata);
                }
            }
        }
        return context.proceed();
    }

    private void stopTimed(String metricName, Timer.Sample sample, String exceptionClass, AnnotationMetadata metadata) {
        try {
            final String description = metadata.getValue(Timed.class, "description", String.class).orElse(null);
            final String[] tags = metadata.getValue(Timed.class, "extraTags", String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
            final double[] percentiles = metadata.getValue(Timed.class, "percentiles", double[].class).orElse(null);
            final boolean histogram = metadata.getValue(Timed.class, "histogram", boolean.class).orElse(false);
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
