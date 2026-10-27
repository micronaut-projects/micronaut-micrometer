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
package io.micronaut.configuration.metrics.micrometer.otlp;

import io.micrometer.registry.otlp.ExemplarContextProvider;
import io.micrometer.registry.otlp.OtlpExemplarContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * An {@link ExemplarContextProvider} that reads the current span from Micrometer Tracing, so that exemplars carry
 * the trace and span ids of sampled spans, or of every span when {@code micronaut.metrics.exemplars.include} is {@code all}.
 *
 * @since 6.2.0
 */
@Internal
final class MicrometerTracingExemplarContextProvider implements ExemplarContextProvider {

    private final Tracer tracer;
    private final boolean allSpans;

    /**
     * @param tracer The tracer
     * @param allSpans Whether exemplars are recorded for spans that are not sampled
     */
    MicrometerTracingExemplarContextProvider(Tracer tracer, boolean allSpans) {
        this.tracer = tracer;
        this.allSpans = allSpans;
    }

    @Override
    public @Nullable OtlpExemplarContext getExemplarContext() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        if (!allSpans && !Boolean.TRUE.equals(context.sampled())) {
            return null;
        }
        return new OtlpExemplarContext(context.traceId(), context.spanId());
    }
}
