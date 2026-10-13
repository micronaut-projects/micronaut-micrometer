package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ExecutionException

class CountedLifecycleSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()
    CountedLifecycleTarget target = ctx.getBean(CountedLifecycleTarget)
    MeterRegistry registry = ctx.getBean(MeterRegistry)
    PollingConditions conditions = new PollingConditions(timeout: 5)

    void "a CompletionStage method that throws an Error before returning its stage is counted"() {
        when:
        target.futureSyncError()

        then:
        def e = thrown(AssertionError)
        e.message == 'direct'
        registry.get("counted.lifecycle.future.sync.error").tags("exception", "AssertionError").counter().count() == 1
    }

    void "recordFailuresOnly is honoured for a Mono"() {
        when:
        target.failuresOnlyMono(false).block()

        then:
        registry.find("counted.lifecycle.failures.mono").counter() == null

        when:
        target.failuresOnlyMono(true).block()

        then:
        thrown(IllegalStateException)
        registry.get("counted.lifecycle.failures.mono").tags("result", "failure", "exception", "IllegalStateException").counter().count() == 1
        registry.find("counted.lifecycle.failures.mono").counters().size() == 1
    }

    void "recordFailuresOnly is honoured for a Flux"() {
        when:
        target.failuresOnlyFlux(false).collectList().block()

        then:
        registry.find("counted.lifecycle.failures.flux").counter() == null

        when:
        target.failuresOnlyFlux(true).collectList().block()

        then:
        thrown(IllegalStateException)
        registry.get("counted.lifecycle.failures.flux").tags("result", "failure", "exception", "IllegalStateException").counter().count() == 1
        registry.find("counted.lifecycle.failures.flux").counters().size() == 1
    }

    void "recordFailuresOnly is honoured for a CompletionStage"() {
        when:
        target.failuresOnlyFuture(false).get()
        Thread.sleep(100)

        then:
        registry.find("counted.lifecycle.failures.future").counter() == null

        when:
        target.failuresOnlyFuture(true).get()

        then:
        thrown(ExecutionException)
        conditions.eventually {
            assert registry.get("counted.lifecycle.failures.future").tags("result", "failure", "exception", "IllegalStateException").counter().count() == 1
        }
        registry.find("counted.lifecycle.failures.future").counters().size() == 1
    }

    void "recordFailuresOnly is honoured for a synchronous method"() {
        when:
        target.failuresOnlySync(false)

        then:
        registry.find("counted.lifecycle.failures.sync").counter() == null

        when:
        target.failuresOnlySync(true)

        then:
        thrown(IllegalStateException)
        registry.get("counted.lifecycle.failures.sync").tags("result", "failure", "exception", "IllegalStateException").counter().count() == 1
    }

    void "the cause of a CompletionException is used for the exception tag"() {
        when:
        target.futureAsyncError().get()

        then:
        thrown(ExecutionException)
        conditions.eventually {
            assert registry.get("counted.lifecycle.future.async.error").tags("result", "failure", "exception", "IllegalStateException").counter().count() == 1
        }
        registry.find("counted.lifecycle.future.async.error").tags("exception", "CompletionException").counter() == null
    }

    void "a reactive method that throws synchronously is counted as a failure"() {
        when:
        target.monoSyncThrow().block()

        then:
        thrown(IllegalStateException)
        registry.get("counted.lifecycle.mono.sync.throw").tags("result", "failure", "exception", "IllegalStateException").counter().count() == 1
    }

    void "a reactive method is counted once per subscription"() {
        when:
        def mono = target.mono()

        then:
        registry.find("counted.lifecycle.mono").counter() == null

        when:
        mono.block()
        mono.block()

        then:
        registry.get("counted.lifecycle.mono").tags("result", "success", "exception", "none").counter().count() == 2
    }

    void "an erroring Flux is counted once as a failure"() {
        when:
        target.fluxError().collectList().block()

        then:
        thrown(IllegalStateException)
        registry.get("counted.lifecycle.flux.error").tags("result", "failure", "exception", "IllegalStateException").counter().count() == 1
        registry.find("counted.lifecycle.flux.error").counters().size() == 1
    }
}
