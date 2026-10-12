package io.micronaut.configuration.metrics.micrometer.prometheus

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MockClock
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micrometer.tracing.Baggage
import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.prometheus.metrics.tracer.common.SpanContext
import jakarta.inject.Singleton
import spock.lang.Specification

class PrometheusExemplarsSpec extends Specification {

    static final String TRACE_ID = '0af7651916cd43dd8448eb211c80319c'
    static final String SPAN_ID = 'b7ad6b7169203331'

    void "exemplars carry the trace of the current span when there is a Tracer bean"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'PrometheusExemplarsSpec'])
        PrometheusMeterRegistry registry = context.getBean(PrometheusMeterRegistry)
        TestTracer tracer = context.getBean(TestTracer)

        when:
        tracer.current = span(true)
        Counter.builder('test.exemplar.counter').register(registry).increment()
        String openMetrics = registry.scrape('application/openmetrics-text')

        then:
        context.getBean(SpanContext) instanceof MicrometerTracingSpanContext
        openMetrics.contains("test_exemplar_counter_total 1.0 # {span_id=\"$SPAN_ID\",trace_id=\"$TRACE_ID\"} 1.0")

        cleanup:
        context.close()
    }

    void "the span context reads the current span"() {
        given:
        TestTracer tracer = new TestTracer()
        SpanContext spanContext = new MicrometerTracingSpanContext(tracer, false)

        expect: 'no span'
        spanContext.currentTraceId == null
        spanContext.currentSpanId == null
        !spanContext.currentSpanSampled

        when:
        tracer.current = span(false)

        then:
        spanContext.currentTraceId == TRACE_ID
        spanContext.currentSpanId == SPAN_ID
        !spanContext.currentSpanSampled

        when:
        tracer.current = span(true)

        then:
        spanContext.currentSpanSampled
    }

    void "exemplars are recorded for spans that are not sampled when include is all"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                        : 'PrometheusExemplarsSpec',
                (PrometheusMeterRegistryFactory.EXEMPLARS_INCLUDE)  : 'all'
        ])
        PrometheusMeterRegistry registry = context.getBean(PrometheusMeterRegistry)
        TestTracer tracer = context.getBean(TestTracer)
        SpanContext spanContext = context.getBean(SpanContext)

        when:
        tracer.current = span(false)
        Counter.builder('test.exemplar.all.counter').register(registry).increment()

        then:
        spanContext.currentSpanSampled
        registry.scrape('application/openmetrics-text').contains("test_exemplar_all_counter_total 1.0 # {span_id=\"$SPAN_ID\",trace_id=\"$TRACE_ID\"} 1.0")

        cleanup:
        context.close()
    }

    void "there are no exemplars without a Tracer bean"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        !context.containsBean(SpanContext)
        context.getBean(PrometheusMeterRegistry)

        cleanup:
        context.close()
    }

    void "exemplars can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                           : 'PrometheusExemplarsSpec',
                (PrometheusMeterRegistryFactory.EXEMPLARS_ENABLED)    : false
        ])
        PrometheusMeterRegistry registry = context.getBean(PrometheusMeterRegistry)
        context.getBean(TestTracer).current = span(true)

        when:
        Counter.builder('test.exemplar.counter').register(registry).increment()

        then:
        !context.containsBean(SpanContext)
        !registry.scrape('application/openmetrics-text').contains(TRACE_ID)

        cleanup:
        context.close()
    }

    void "a SpanContext bean takes precedence over the one created from the Tracer"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'PrometheusExemplarsSpec', 'custom.span-context': true])

        expect:
        context.getBean(SpanContext) instanceof CustomSpanContext
        context.getBean(PrometheusMeterRegistry)

        cleanup:
        context.close()
    }

    void "the registry uses the Clock bean"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'PrometheusExemplarsSpec'])

        expect:
        context.getBean(PrometheusMeterRegistry).config().clock().is(context.getBean(Clock))

        cleanup:
        context.close()
    }

    static Span span(boolean sampled) {
        TraceContext traceContext = [
                traceId : { TRACE_ID },
                spanId  : { SPAN_ID },
                parentId: { null },
                sampled : { sampled }
        ] as TraceContext
        return [context: { traceContext }, isNoop: { false }] as Span
    }

    /**
     * A Tracer whose current span is set by the test.
     */
    static class TestTracer implements Tracer {
        @Delegate(interfaces = false)
        private final Tracer delegate = Tracer.NOOP
        Span current

        @Override
        Span currentSpan() {
            current
        }

        @Override
        Baggage createBaggage(String name) {
            delegate.createBaggage(name)
        }

        @Override
        Baggage createBaggage(String name, String value) {
            delegate.createBaggage(name, value)
        }
    }

    static class CustomSpanContext implements SpanContext {
        String currentTraceId
        String currentSpanId
        boolean currentSpanSampled

        @Override
        void markCurrentSpanAsExemplar() {
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'PrometheusExemplarsSpec')
    static class TestTracerFactory {

        @Singleton
        TestTracer tracer() {
            new TestTracer()
        }

        @Singleton
        Clock clock() {
            new MockClock()
        }

        @Singleton
        @Requires(property = 'custom.span-context')
        CustomSpanContext customSpanContext() {
            new CustomSpanContext()
        }
    }
}
