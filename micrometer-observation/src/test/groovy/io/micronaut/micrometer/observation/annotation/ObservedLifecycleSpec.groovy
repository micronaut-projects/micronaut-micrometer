package io.micronaut.micrometer.observation.annotation

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException

class ObservedLifecycleSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ObservedLifecycleSpec'])
    ObservedLifecycleTarget target = ctx.getBean(ObservedLifecycleTarget)
    RecordingHandler handler = ctx.getBean(RecordingHandler)
    PollingConditions conditions = new PollingConditions(timeout: 5)

    void "a Mono that is never subscribed does not start an observation"() {
        when:
        target.mono(Mono.just(1))

        then:
        handler.events("observed.lifecycle.mono").isEmpty()
    }

    void "a cancelled Mono stops the observation once"() {
        when:
        def disposable = target.mono(Mono.never()).subscribe()

        then:
        handler.events("observed.lifecycle.mono") == ["start"]

        when:
        disposable.dispose()
        disposable.dispose()

        then:
        handler.events("observed.lifecycle.mono") == ["start", "stop"]
    }

    void "a cancelled Flux, for example by a timeout, stops the observation"() {
        when:
        target.flux(Flux.never()).timeout(Duration.ofMillis(20)).collectList().block()

        then:
        thrown(Exception)
        conditions.eventually {
            assert handler.events("observed.lifecycle.flux") == ["start", "stop"]
        }
    }

    void "a Flux observation is stopped on completion rather than on the first element"() {
        given:
        Sinks.Many<Integer> sink = Sinks.many().unicast().onBackpressureBuffer()
        List<Integer> received = []

        when:
        target.flux(sink.asFlux()).subscribe({ received << it })
        sink.tryEmitNext(1)
        sink.tryEmitNext(2)

        then:
        received == [1, 2]
        handler.events("observed.lifecycle.flux") == ["start"]

        when:
        sink.tryEmitComplete()

        then:
        handler.events("observed.lifecycle.flux") == ["start", "stop"]
    }

    void "an erroring Flux records the error once and stops the observation"() {
        when:
        target.flux(Flux.concat(Flux.just(1), Flux.error(new IllegalStateException("boom")))).collectList().block()

        then:
        thrown(IllegalStateException)
        handler.events("observed.lifecycle.flux") == ["start", "error:IllegalStateException", "stop"]
    }

    void "the observation starts on subscribe and each subscription is observed"() {
        when:
        def mono = target.mono(Mono.just(1))

        then:
        handler.events("observed.lifecycle.mono").isEmpty()

        when:
        mono.block()
        mono.block()

        then:
        handler.events("observed.lifecycle.mono") == ["start", "stop", "start", "stop"]
    }

    void "a Mono consumed with next() is not treated as cancelled before it emits"() {
        when:
        Integer value = Flux.from(target.mono(Mono.just(1))).next().block()

        then:
        value == 1
        handler.events("observed.lifecycle.mono") == ["start", "stop"]
    }

    void "a synchronous exception is recorded once"() {
        when:
        target.syncThrow()

        then:
        thrown(IllegalStateException)
        handler.events("observed.lifecycle.sync.throw") == ["start", "error:IllegalStateException", "stop"]
    }

    void "a reactive method that throws synchronously records the error once"() {
        when:
        target.monoSyncThrow().block()

        then:
        thrown(IllegalStateException)
        handler.events("observed.lifecycle.mono.sync.throw") == ["start", "error:IllegalStateException", "stop"]
    }

    void "a CompletionStage method that throws synchronously records the error once"() {
        when:
        target.futureSyncThrow().get()

        then:
        thrown(ExecutionException)
        handler.events("observed.lifecycle.future.sync.throw") == ["start", "error:IllegalStateException", "stop"]
    }

    void "a CompletionStage method that throws an Error before returning its stage stops the observation"() {
        when:
        target.futureSyncError()

        then:
        def e = thrown(AssertionError)
        e.message == 'direct'
        handler.events("observed.lifecycle.future.sync.error") == ["start", "error:AssertionError", "stop"]
    }

    void "concurrent first invocations of a method with expressions observe their own evaluated values"() {
        given:
        CyclicBarrier barrier = new CyclicBarrier(2)

        when:
        List<String> results = ['alpha', 'beta'].collect { String arg ->
            CompletableFuture.supplyAsync {
                FirstCallBarrier.arm(barrier)
                target.concurrentExpression(arg)
            }
        }*.get(10, TimeUnit.SECONDS)

        then:
        results == ['alpha', 'beta']
        handler.events("observed.lifecycle.concurrent.alpha") == ["start", "stop"]
        handler.events("observed.lifecycle.concurrent.beta") == ["start", "stop"]
        handler.keyValue("observed.lifecycle.concurrent.alpha", "arg") == "alpha"
        handler.keyValue("observed.lifecycle.concurrent.beta", "arg") == "beta"

        when: 'the cached method is invoked again'
        target.concurrentExpression('gamma')

        then:
        handler.events("observed.lifecycle.concurrent.gamma") == ["start", "stop"]
    }

    void "the cause of a CompletionException is recorded as the error"() {
        when:
        target.futureAsyncError().get()

        then:
        thrown(ExecutionException)
        conditions.eventually {
            assert handler.events("observed.lifecycle.future.async.error") == ["start", "error:IllegalStateException", "stop"]
        }
    }

    void "the observation is available from the Reactor context of the subscription"() {
        when:
        Optional<Observation> observation = target.observationFromContext().block()

        then:
        observation.isPresent()
        observation.get().context.name == "observed.lifecycle.context"
        handler.events("observed.lifecycle.context") == ["start", "stop"]
    }

    @Singleton
    @Requires(property = "spec.name", value = "ObservedLifecycleSpec")
    static class RecordingHandler implements ObservationHandler<Observation.Context> {

        private final Queue<List<String>> events = new ConcurrentLinkedQueue<>()
        private final Map<String, Observation.Context> started = new ConcurrentHashMap<>()

        String keyValue(String name, String key) {
            started[name]?.getLowCardinalityKeyValue(key)?.value
        }

        List<String> events(String name) {
            events.findAll { it[0] == name }.collect { it[1] }
        }

        @Override
        void onStart(Observation.Context context) {
            started[context.name] = context
            events.add([context.name, "start"])
        }

        @Override
        void onError(Observation.Context context) {
            events.add([context.name, "error:" + context.error.class.simpleName])
        }

        @Override
        void onStop(Observation.Context context) {
            events.add([context.name, "stop"])
        }

        @Override
        boolean supportsContext(Observation.Context context) {
            return true
        }
    }
}
