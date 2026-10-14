package io.micronaut.micrometer.observation.annotation

import io.micrometer.observation.ObservationRegistry
import io.micronaut.context.ApplicationContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class ObservedNoopRegistrySpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ObservedNoopRegistrySpec'])
    ObservedLifecycleTarget target = ctx.getBean(ObservedLifecycleTarget)

    void "the registry has no handler"() {
        expect:
        ctx.getBean(ObservationRegistry).isNoop()
    }

    void "reactive results are returned as they are, without an observation in their context"() {
        given:
        Mono<Integer> mono = Mono.just(1)
        Flux<Integer> flux = Flux.just(1, 2)

        expect:
        target.mono(mono).is(mono)
        target.flux(flux).is(flux)
        target.flux(flux).collectList().block() == [1, 2]
        target.observationFromContext().block() == Optional.empty()
    }

    void "a synchronous method that throws throws"() {
        when:
        target.syncThrow()

        then:
        thrown(IllegalStateException)
    }

    void "a reactive method that throws synchronously returns a failed publisher"() {
        when:
        Mono<Integer> mono = target.monoSyncThrow()

        then:
        noExceptionThrown()

        when:
        mono.block()

        then:
        thrown(IllegalStateException)
    }

    void "a CompletionStage method that throws synchronously returns a failed stage"() {
        when:
        CompletableFuture<Integer> future = target.futureSyncThrow()

        then:
        noExceptionThrown()

        when:
        future.get()

        then:
        ExecutionException e = thrown()
        e.cause instanceof IllegalStateException
    }

    void "a CompletionStage method that fails asynchronously returns its failed stage"() {
        when:
        target.futureAsyncError().get()

        then:
        ExecutionException e = thrown()
        e.cause instanceof IllegalStateException
    }
}
