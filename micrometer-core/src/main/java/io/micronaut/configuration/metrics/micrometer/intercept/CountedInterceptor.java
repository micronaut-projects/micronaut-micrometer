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
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

import static io.micrometer.core.aop.TimedAspect.EXCEPTION_TAG;

/**
 * Implements support for {@link io.micrometer.core.annotation.Counted} as AOP advice.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@RequiresMetrics
@InterceptorBean(Counted.class)
public class CountedInterceptor implements MethodInterceptor<Object, Object> {

    public static final String DEFAULT_METRIC_NAME = "method.counted";
    public static final String RESULT_TAG = "result";

    private final MeterRegistry meterRegistry;

    public CountedInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final String metricName = metadata.stringValue(Counted.class).orElse(DEFAULT_METRIC_NAME);
        if (StringUtils.isNotEmpty(metricName)) {
            InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
            try {
                InterceptedMethod.ResultType resultType = interceptedMethod.resultType();
                switch (resultType) {
                    case PUBLISHER:
                        Object interceptResult = context.proceed();
                        if (interceptResult == null) {
                            return null;
                        }
                        Object reactiveResult;
                        if (context.getReturnType().isSingleResult()) {
                            Mono<?> single = Mono.from(Publishers.convertPublisher(interceptResult, Publisher.class));
                            reactiveResult = single
                                    .doOnError(throwable -> doCount(metadata, metricName, throwable))
                                    .doOnSuccess(o -> doCount(metadata, metricName, null));
                        } else {
                            Flux<?> flowable = Flux.from(Publishers.convertPublisher(interceptResult, Publisher.class));
                            reactiveResult = flowable
                                    .doOnError(throwable -> doCount(metadata, metricName, throwable))
                                    .doOnComplete(() -> doCount(metadata, metricName, null));
                        }
                        return Publishers.convertPublisher(reactiveResult, context.getReturnType().getType());
                    case COMPLETION_STAGE:
                        CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                        CompletionStage<?> completionStageResult = completionStage
                                .whenComplete((o, throwable) -> doCount(metadata, metricName, throwable));

                        return interceptedMethod.handleResult(completionStageResult);
                    case SYNCHRONOUS:
                        final Object result = context.proceed();
                        try {
                            return result;
                        } finally {
                            if (metadata.isFalse(Counted.class, "recordFailuresOnly")) {
                                doCount(metadata, metricName, null);
                            }
                        }
                    default:
                        return interceptedMethod.unsupported();
                }
            } catch (Exception e) {
                try {
                    return interceptedMethod.handleException(e);
                } finally {
                    doCount(metadata, metricName, e);
                }
            }
        }
        return context.proceed();
    }

    private void doCount(AnnotationMetadata metadata, String metricName, @Nullable Throwable e) {
        Counter.builder(metricName)
                .tags(metadata.stringValues(Counted.class, "extraTags"))
                .description(metadata.stringValue(Counted.class, "description").orElse(null))
                .tag(EXCEPTION_TAG, e != null ? e.getClass().getSimpleName() : "none")
                .tag(RESULT_TAG, e != null ? "failure" : "success")
                .register(meterRegistry)
                .increment();
    }
}
