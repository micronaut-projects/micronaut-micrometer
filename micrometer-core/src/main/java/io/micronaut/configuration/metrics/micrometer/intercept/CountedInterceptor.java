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
import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(CountedInterceptor.class);

    private final MeterRegistry meterRegistry;
    private final ConversionService conversionService;
    private final List<AbstractMethodTagger> methodTaggers;

    /**
     * @param meterRegistry The meter registry
     * @deprecated Pass conversion service in new constructor
     */
    @Deprecated
    public CountedInterceptor(MeterRegistry meterRegistry) {
        this(meterRegistry, ConversionService.SHARED);
    }

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @deprecated Pass list of MethodTagger in new constructor
     */
    @Deprecated
    public CountedInterceptor(MeterRegistry meterRegistry, ConversionService conversionService) {
        this(meterRegistry, conversionService, Collections.emptyList());
    }

    /**
     * @param meterRegistry The meter registry
     * @param conversionService The conversion service
     * @param methodTaggers Additional tag builders
     */
    @Inject
    public CountedInterceptor(MeterRegistry meterRegistry, ConversionService conversionService, List<AbstractMethodTagger> methodTaggers) {
        this.meterRegistry = meterRegistry;
        this.conversionService = conversionService;
        this.methodTaggers = Objects.requireNonNullElse(methodTaggers, Collections.emptyList());
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final String metricName = metadata.stringValue(Counted.class).orElse(DEFAULT_METRIC_NAME);
        if (StringUtils.isNotEmpty(metricName)) {
            InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
            try {
                InterceptedMethod.ResultType resultType = interceptedMethod.resultType();
                switch (resultType) {
                    case PUBLISHER -> {
                        Object interceptResult = context.proceed();
                        if (interceptResult == null) {
                            return null;
                        }
                        Object reactiveResult;
                        if (context.getReturnType().isSingleResult()) {
                            Mono<?> single = Mono.from(Publishers.convertPublisher(conversionService, interceptResult, Publisher.class));
                            reactiveResult = single
                                .doOnError(throwable -> doCount(metadata, metricName, context, throwable))
                                .doOnSuccess(o -> doCount(metadata, metricName, context, null));
                        } else {
                            Flux<?> flowable = Flux.from(Publishers.convertPublisher(conversionService, interceptResult, Publisher.class));
                            reactiveResult = flowable
                                .doOnError(throwable -> doCount(metadata, metricName, context, throwable))
                                .doOnComplete(() -> doCount(metadata, metricName, context, null));
                        }
                        return Publishers.convertPublisher(conversionService, reactiveResult, context.getReturnType().getType());
                    }
                    case COMPLETION_STAGE -> {
                        CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                        CompletionStage<?> completionStageResult = completionStage
                            .whenComplete((o, throwable) -> doCount(metadata, metricName, context, throwable));
                        return interceptedMethod.handleResult(completionStageResult);
                    }
                    case SYNCHRONOUS -> {
                        final Object result = context.proceed();
                        try {
                            return result;
                        } finally {
                            if (metadata.isFalse(Counted.class, "recordFailuresOnly")) {
                                doCount(metadata, metricName, context, null);
                            }
                        }
                    }
                    default -> {
                        return interceptedMethod.unsupported();
                    }
                }
            } catch (Exception e) {
                try {
                    return interceptedMethod.handleException(e);
                } finally {
                    doCount(metadata, metricName, context, e);
                }
            }
        }
        return context.proceed();
    }

    private void doCount(AnnotationMetadata metadata, String metricName, MethodInvocationContext<Object, Object> context,  @Nullable Throwable e) {
        Counter.builder(metricName)
                .tags(metadata.stringValues(Counted.class, "extraTags"))
                .tags(
                    methodTaggers.isEmpty() ? Collections.emptyList() :
                        methodTaggers
                            .stream()
                            .flatMap(b -> b.getTags(context).stream())
                            .toList()
                )
                .description(metadata.stringValue(Counted.class, "description").orElse(null))
                .tag(EXCEPTION_TAG, e != null ? e.getClass().getSimpleName() : "none")
                .tag(RESULT_TAG, e != null ? "failure" : "success")
                .register(meterRegistry)
                .increment();
    }
}
