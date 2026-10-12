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
package io.micronaut.configuration.metrics.micrometer.prometheus;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micronaut.core.annotation.Internal;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.jspecify.annotations.Nullable;

/**
 * A Prometheus {@link SpanContext} that reads the current span from Micrometer Tracing, so that exemplars carry the
 * trace and span ids of sampled spans, or of every span when {@code micronaut.metrics.exemplars.include} is {@code all}.
 *
 * @since 6.2.0
 */
@Internal
final class MicrometerTracingSpanContext implements SpanContext {

    private final Tracer tracer;
    private final boolean allSpans;

    /**
     * @param tracer The tracer
     * @param allSpans Whether exemplars are recorded for spans that are not sampled
     */
    MicrometerTracingSpanContext(Tracer tracer, boolean allSpans) {
        this.tracer = tracer;
        this.allSpans = allSpans;
    }

    @Override
    public @Nullable String getCurrentTraceId() {
        TraceContext context = currentContext();
        return context != null ? context.traceId() : null;
    }

    @Override
    public @Nullable String getCurrentSpanId() {
        TraceContext context = currentContext();
        return context != null ? context.spanId() : null;
    }

    @Override
    public boolean isCurrentSpanSampled() {
        TraceContext context = currentContext();
        return context != null && (allSpans || Boolean.TRUE.equals(context.sampled()));
    }

    @Override
    public void markCurrentSpanAsExemplar() {
        // Micrometer Tracing has no way to keep a span because it is an exemplar
    }

    private @Nullable TraceContext currentContext() {
        Span span = tracer.currentSpan();
        return span != null ? span.context() : null;
    }
}
