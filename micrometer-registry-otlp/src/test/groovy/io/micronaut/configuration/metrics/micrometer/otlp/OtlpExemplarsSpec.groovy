package io.micronaut.configuration.metrics.micrometer.otlp

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MockClock
import io.micrometer.registry.otlp.ExemplarContextProvider
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.micrometer.registry.otlp.OtlpMetricsSender
import io.micrometer.tracing.Baggage
import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadFactory

class OtlpExemplarsSpec extends Specification {

    static final String TRACE_ID = '0af7651916cd43dd8448eb211c80319c'
    static final String SPAN_ID = 'b7ad6b7169203331'

    void "the registry is built with the optional beans and records exemplars from the current span"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'OtlpExemplarsSpec'])
        OtlpMeterRegistry registry = context.getBean(OtlpMeterRegistry)
        TestSender sender = context.getBean(TestSender)
        context.getBean(TestTracer).current = span(true)

        when:
        Counter.builder('test.exemplar.counter').register(registry).increment()
        registry.close()
        // opentelemetry-proto is a runtime dependency of micrometer-registry-otlp, so the request is read dynamically
        def requestType = Class.forName('io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest')
        List exemplars = sender.requests
                .collect { requestType.parseFrom(it.metricsData) }
                .collectMany { it.resourceMetricsList }
                .collectMany { it.scopeMetricsList }
                .collectMany { it.metricsList }
                .findAll { it.name == 'test.exemplar.counter' }
                .collectMany { it.sum.dataPointsList }
                .collectMany { it.exemplarsList }

        then:
        context.getBean(ExemplarContextProvider) instanceof MicrometerTracingExemplarContextProvider
        registry.config().clock().is(context.getBean(Clock))
        context.getBean(TestThreadFactory).used
        !sender.requests.isEmpty()
        exemplars.size() == 1
        exemplars[0].traceId.toByteArray().encodeHex().toString() == TRACE_ID
        exemplars[0].spanId.toByteArray().encodeHex().toString() == SPAN_ID

        cleanup:
        context.close()
    }

    void "the exemplar context provider only uses sampled spans"() {
        given:
        TestTracer tracer = new TestTracer()
        ExemplarContextProvider provider = new MicrometerTracingExemplarContextProvider(tracer, false)

        expect: 'no span'
        provider.exemplarContext == null

        when:
        tracer.current = span(false)

        then:
        provider.exemplarContext == null

        when:
        tracer.current = span(true)

        then:
        provider.exemplarContext.traceId == TRACE_ID
        provider.exemplarContext.spanId == SPAN_ID
    }

    void "the exemplar context provider uses every span when include is all"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                                 : 'OtlpExemplarsSpec',
                (OtlpMeterRegistryFactory.EXEMPLARS_INCLUDE) : 'all'
        ])
        context.getBean(TestTracer).current = span(false)

        expect:
        context.getBean(ExemplarContextProvider).exemplarContext.traceId == TRACE_ID

        cleanup:
        context.close()
    }

    void "there are no exemplars without a Tracer bean or when they are disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(properties)

        expect:
        context.getBean(OtlpMeterRegistry)
        !context.containsBean(ExemplarContextProvider)

        cleanup:
        context.close()

        where:
        properties << [
                [:],
                ['spec.name': 'OtlpExemplarsSpec', (OtlpMeterRegistryFactory.EXEMPLARS_ENABLED): false]
        ]
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

    static class TestSender implements OtlpMetricsSender {
        final List<OtlpMetricsSender.Request> requests = new CopyOnWriteArrayList<>()

        @Override
        void send(OtlpMetricsSender.Request request) {
            requests << request
        }
    }

    static class TestThreadFactory implements ThreadFactory {
        volatile boolean used

        @Override
        Thread newThread(Runnable r) {
            used = true
            Thread thread = new Thread(r, 'test-otlp')
            thread.daemon = true
            thread
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'OtlpExemplarsSpec')
    static class TestBeans {

        @Singleton
        TestTracer tracer() {
            new TestTracer()
        }

        @Singleton
        TestSender sender() {
            new TestSender()
        }

        @Singleton
        Clock clock() {
            new MockClock()
        }

        @Singleton
        @Named(OtlpMeterRegistryFactory.OTLP_THREAD_FACTORY)
        TestThreadFactory threadFactory() {
            new TestThreadFactory()
        }
    }
}
