/*
 * Copyright 2017-2026 original authors
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

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger;
import io.micronaut.configuration.metrics.annotation.MetricOptions;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.order.OrderUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Helpers shared by the {@code @Timed} and {@code @Counted} interceptors.
 */
final class MethodMetricsSupport {

    /**
     * Exception tag value used when there is no exception.
     */
    static final String NONE = "none";

    /**
     * Exception tag value used when a reactive subscription is cancelled before it completes. This is the value the
     * Micrometer aspects record for a cancelled {@link java.util.concurrent.CompletableFuture}.
     */
    static final String CANCELLED = CancellationException.class.getSimpleName();

    private static final AbstractMethodTagger[] NO_TAGGERS = new AbstractMethodTagger[0];

    private MethodMetricsSupport() {
    }

    /**
     * @param throwable The exception
     * @return The exception tag value
     */
    static String exceptionTag(Throwable throwable) {
        return throwable.getClass().getSimpleName();
    }

    /**
     * Returns the exception tag value for an asynchronous failure, unwrapping the {@link CompletionException} and
     * {@link ExecutionException} wrappers added by {@link java.util.concurrent.CompletableFuture}.
     *
     * @param throwable The exception
     * @return The exception tag value
     */
    static String asyncExceptionTag(Throwable throwable) {
        Throwable t = throwable;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return exceptionTag(t);
    }

    /**
     * Resolves the taggers that apply to a method, in order.
     *
     * @param methodTaggers All taggers
     * @param metadata The method metadata
     * @return The taggers
     */
    static AbstractMethodTagger[] resolveTaggers(List<AbstractMethodTagger> methodTaggers, AnnotationMetadata metadata) {
        if (methodTaggers.isEmpty()) {
            return NO_TAGGERS;
        }
        boolean filter = metadata.booleanValue(MetricOptions.class, MetricOptions.MEMBER_FILTER_TAGGERS).orElse(false);
        List<Class<? extends AbstractMethodTagger>> taggers = filter
            ? Arrays.asList(metadata.classValues(MetricOptions.class, MetricOptions.MEMBER_TAGGERS))
            : Collections.emptyList();
        List<AbstractMethodTagger> sorted = new ArrayList<>(methodTaggers);
        sorted.sort(OrderUtil.ORDERED_COMPARATOR);
        if (filter) {
            sorted.removeIf(t -> !taggers.contains(t.getClass()));
        }
        return sorted.toArray(NO_TAGGERS);
    }

    /**
     * @param taggers The taggers
     * @param context The invocation context
     * @return The tags produced by the taggers
     */
    static List<Tag> taggerTags(AbstractMethodTagger[] taggers, MethodInvocationContext<Object, Object> context) {
        if (taggers.length == 0) {
            return Collections.emptyList();
        }
        if (taggers.length == 1) {
            return taggers[0].getTags(context);
        }
        List<Tag> tags = new ArrayList<>();
        for (AbstractMethodTagger tagger : taggers) {
            tags.addAll(tagger.getTags(context));
        }
        return tags;
    }

    /**
     * Parses the extra tags declared on an annotation once, so they are not re-parsed on every invocation.
     *
     * @param extraTags The key/value pairs
     * @return The tags, or {@code null} if they are invalid, in which case the raw values are applied when the meter
     * is registered so the error is reported as before
     */
    static @Nullable Tags parseTags(String[] extraTags) {
        try {
            return Tags.of(extraTags);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
