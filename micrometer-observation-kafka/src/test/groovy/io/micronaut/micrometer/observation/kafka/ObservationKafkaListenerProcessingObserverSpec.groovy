package io.micronaut.micrometer.observation.kafka

import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micronaut.configuration.kafka.KafkaConsumerProcessingObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ObservationKafkaListenerProcessingObserverSpec extends Specification {

    private static final String SPEC_NAME = 'ObservationKafkaListenerProcessingObserverSpec'

    void 'the observer is a bean by default'() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.getBean(KafkaConsumerProcessingObserver) instanceof ObservationKafkaListenerProcessingObserver

        cleanup:
        context.close()
    }

    void 'the observer can be disabled'() {
        given:
        ApplicationContext context = ApplicationContext.run(['micrometer.observation.kafka.enabled': false])

        expect:
        !context.containsBean(KafkaConsumerProcessingObserver)

        cleanup:
        context.close()
    }

    void 'a record processed after a retry is one observation, current only during each attempt'() {
        given:
        RecordingHandler handler = new RecordingHandler()
        ObservationRegistry registry = registry(handler)
        ObservationKafkaListenerProcessingObserver observer = new ObservationKafkaListenerProcessingObserver(registry, null)
        ConsumerRecord<String, String> record = new ConsumerRecord<>('orders', 3, 42L, 'key', 'value')

        when:
        Object handle = observer.onStart(record, 'orders-client', 'orders-group')
        Object firstAttempt = observer.onAttemptStart(handle)
        Observation currentInFirstAttempt = registry.currentObservation
        observer.onAttemptEnd(firstAttempt)
        Observation currentBetweenAttempts = registry.currentObservation
        Object secondAttempt = observer.onAttemptStart(handle)
        observer.onAttemptEnd(secondAttempt)
        observer.onSuccess(handle)

        then:
        currentInFirstAttempt.is(handle)
        currentBetweenAttempts == null
        registry.currentObservation == null
        handler.started.size() == 1
        handler.stopped.size() == 1

        when:
        KafkaListenerObservationContext context = handler.stopped.first() as KafkaListenerObservationContext

        then:
        context.name == 'messaging.kafka.listener'
        context.contextualName == 'orders receive'
        context.error == null
        context.lowCardinalityKeyValues == KeyValues.of(
                KeyValue.of('messaging.system', 'kafka'),
                KeyValue.of('messaging.operation', 'receive'),
                KeyValue.of('messaging.destination.name', 'orders'),
                KeyValue.of('messaging.kafka.consumer.group', 'orders-group'),
                KeyValue.of('error', KeyValue.NONE_VALUE))
        context.highCardinalityKeyValues == KeyValues.of(
                KeyValue.of('messaging.kafka.client.id', 'orders-client'),
                KeyValue.of('messaging.kafka.destination.partition', '3'),
                KeyValue.of('messaging.kafka.message.offset', '42'))
    }

    void 'a record that fails records the error'() {
        given:
        RecordingHandler handler = new RecordingHandler()
        ObservationKafkaListenerProcessingObserver observer = new ObservationKafkaListenerProcessingObserver(registry(handler), null)
        ConsumerRecord<String, String> record = new ConsumerRecord<>('orders', 0, 0L, 'key', 'value')
        IllegalStateException error = new IllegalStateException('boom')

        when:
        Object handle = observer.onStart(record, 'orders-client', null)
        observer.onAttemptEnd(observer.onAttemptStart(handle))
        observer.onError(handle, error)

        then:
        handler.stopped.size() == 1
        with(handler.stopped.first() as KafkaListenerObservationContext) {
            it.error.is(error)
            it.lowCardinalityKeyValues.stream().anyMatch { it == KeyValue.of('error', 'IllegalStateException') }
            it.lowCardinalityKeyValues.stream().anyMatch { it == KeyValue.of('messaging.kafka.consumer.group', 'unknown') }
        }
    }

    void 'the propagated context is read from the record headers'() {
        given:
        ConsumerRecord<String, String> record = new ConsumerRecord<>('orders', 0, 0L, 'key', 'value')
        record.headers().add(new RecordHeader('traceparent', '00-trace-span-01'.getBytes(StandardCharsets.UTF_8)))
        record.headers().add(new RecordHeader('tracestate', null))
        KafkaListenerObservationContext context = new KafkaListenerObservationContext(record, 'orders-client', 'orders-group')

        expect:
        context.getter.get(record, 'traceparent') == '00-trace-span-01'
        context.getter.get(record, 'tracestate') == null
        context.getter.get(record, 'baggage') == null
    }

    void 'a convention bean replaces the default convention'() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': SPEC_NAME])
        RecordingHandler handler = new RecordingHandler()
        context.getBean(ObservationRegistry).observationConfig().observationHandler(handler)
        KafkaConsumerProcessingObserver observer = context.getBean(KafkaConsumerProcessingObserver)

        when:
        Object handle = observer.onStart(new ConsumerRecord<>('orders', 0, 0L, 'key', 'value'), 'orders-client', 'orders-group')
        observer.onSuccess(handle)

        then:
        handler.stopped*.name == ['custom.listener']

        cleanup:
        context.close()
    }

    private static ObservationRegistry registry(ObservationHandler<Observation.Context> handler) {
        ObservationRegistry registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(handler)
        registry
    }

    static class RecordingHandler implements ObservationHandler<Observation.Context> {
        final List<Observation.Context> started = []
        final List<Observation.Context> stopped = []

        @Override
        void onStart(Observation.Context context) {
            started << context
        }

        @Override
        void onStop(Observation.Context context) {
            stopped << context
        }

        @Override
        boolean supportsContext(Observation.Context context) {
            true
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class CustomConvention implements KafkaListenerObservationConvention {
        @Override
        String getName() {
            'custom.listener'
        }
    }
}
