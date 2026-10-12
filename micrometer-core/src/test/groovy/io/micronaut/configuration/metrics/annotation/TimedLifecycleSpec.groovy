package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static java.util.concurrent.TimeUnit.MILLISECONDS

class TimedLifecycleSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()
    TimedLifecycleTarget target = ctx.getBean(TimedLifecycleTarget)
    MeterRegistry registry = ctx.getBean(MeterRegistry)
    PollingConditions conditions = new PollingConditions(timeout: 5)

    void "an erroring Flux records the timer once with the exception tag"() {
        when:
        target.fluxError().collectList().block()

        then:
        thrown(IllegalStateException)
        registry.get("timed.lifecycle.flux.error").tags("exception", "IllegalStateException").timer().count() == 1
        registry.find("timed.lifecycle.flux.error").timers().size() == 1
    }

    void "a cancelled Flux records the timer once with a CancellationException tag"() {
        when:
        Disposable disposable = target.fluxNever().subscribe()
        disposable.dispose()
        disposable.dispose()

        then:
        registry.get("timed.lifecycle.flux.never").tags("exception", "CancellationException").timer().count() == 1
        registry.find("timed.lifecycle.flux.never").timers().size() == 1
    }

    void "a Flux timer starts on subscribe and is not restarted by request batches"() {
        when:
        List<Integer> values = target.fluxSlow(50, 4).limitRate(5).collectList().block()

        then:
        values.size() == 50
        def timer = registry.get("timed.lifecycle.flux.slow").tags("exception", "none").timer()
        timer.count() == 1
        // 50 elements delayed by 4ms each; restarting the sample on every request(n) would record only the tail
        timer.totalTime(MILLISECONDS) >= 150
    }

    void "a Mono timer starts on subscribe, not when the method is invoked"() {
        when:
        def mono = target.monoDelayed(10)
        Thread.sleep(300)
        mono.block()

        then:
        def timer = registry.get("timed.lifecycle.mono.delayed").tags("exception", "none").timer()
        timer.count() == 1
        timer.totalTime(MILLISECONDS) < 250
    }

    void "a Mono that is cancelled downstream after emitting is recorded as a success"() {
        when:
        Integer value = Flux.from(target.mono()).next().block()

        then:
        value == 1
        registry.get("timed.lifecycle.mono").tags("exception", "none").timer().count() == 1
        registry.find("timed.lifecycle.mono").timers().size() == 1
    }

    void "a Mono that is never subscribed records nothing"() {
        when:
        target.mono()

        then:
        registry.find("timed.lifecycle.mono").timer() == null
    }

    void "a reactive method that throws synchronously is recorded"() {
        when:
        target.monoSyncThrow().block()

        then:
        thrown(IllegalStateException)
        registry.get("timed.lifecycle.mono.sync.throw").tags("exception", "IllegalStateException").timer().count() == 1
    }

    void "a CompletionStage method that throws synchronously is recorded"() {
        when:
        target.futureSyncThrow().get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof IllegalStateException
        registry.get("timed.lifecycle.future.sync.throw").tags("exception", "IllegalStateException").timer().count() == 1
    }

    void "the cause of a CompletionException is used for the exception tag"() {
        when:
        target.futureAsyncError().get()

        then:
        thrown(ExecutionException)
        conditions.eventually {
            assert registry.get("timed.lifecycle.future.async.error").tags("exception", "IllegalStateException").timer().count() == 1
        }
        registry.find("timed.lifecycle.future.async.error").tags("exception", "CompletionException").timer() == null
    }

    void "longTask = true records a LongTaskTimer for a blocking method"() {
        given:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)

        when:
        CompletableFuture<Integer> result = CompletableFuture.supplyAsync { target.longTask(started, release) }
        started.await(5, TimeUnit.SECONDS)

        then:
        def longTaskTimer = registry.get("timed.lifecycle.long.task").tags("kind", "blocking").longTaskTimer()
        longTaskTimer.activeTasks() == 1
        registry.find("timed.lifecycle.long.task").timer() == null

        when:
        release.countDown()

        then:
        result.get(5, TimeUnit.SECONDS) == 1
        longTaskTimer.activeTasks() == 0
    }

    void "longTask = true tracks a reactive method from subscription until it terminates"() {
        given:
        Sinks.One<Integer> sink = Sinks.one()

        when:
        def mono = target.longTaskMono(sink.asMono())

        then: 'nothing is active before subscription'
        (registry.find("timed.lifecycle.long.task.mono").longTaskTimer()?.activeTasks() ?: 0) == 0

        when:
        def disposable = mono.subscribe()

        then:
        registry.get("timed.lifecycle.long.task.mono").longTaskTimer().activeTasks() == 1

        when:
        sink.tryEmitValue(1)

        then:
        registry.get("timed.lifecycle.long.task.mono").longTaskTimer().activeTasks() == 0

        when: 'a second subscription is cancelled'
        Sinks.One<Integer> sink2 = Sinks.one()
        def disposable2 = target.longTaskMono(sink2.asMono()).subscribe()

        then:
        registry.get("timed.lifecycle.long.task.mono").longTaskTimer().activeTasks() == 1

        when:
        disposable2.dispose()

        then:
        registry.get("timed.lifecycle.long.task.mono").longTaskTimer().activeTasks() == 0

        cleanup:
        disposable?.dispose()
    }

    void "longTask = true tracks a CompletionStage method until it completes"() {
        given:
        CompletableFuture<Integer> source = new CompletableFuture<>()

        when:
        def result = target.longTaskFuture(source)

        then:
        registry.get("timed.lifecycle.long.task.future").longTaskTimer().activeTasks() == 1

        when:
        source.complete(1)

        then:
        result.toCompletableFuture().get(5, TimeUnit.SECONDS) == 1
        registry.get("timed.lifecycle.long.task.future").longTaskTimer().activeTasks() == 0
    }

    void "a regular timer and a long task timer can be combined"() {
        when:
        target.timerAndLongTask()

        then:
        registry.get("timed.lifecycle.both.timer").timer().count() == 1
        registry.get("timed.lifecycle.both.long.task").longTaskTimer().activeTasks() == 0
        registry.find("timed.lifecycle.both.long.task").timer() == null
    }

    void "the timer is recorded again for each subscription"() {
        when:
        def mono = target.mono()
        mono.block()
        mono.block()

        then:
        registry.get("timed.lifecycle.mono").tags("exception", "none").timer().count() == 2
    }

    void "a slow Flux consumed with a timeout is recorded once as cancelled"() {
        when:
        target.fluxSlow(10, 500).timeout(Duration.ofMillis(50)).collectList().block()

        then:
        thrown(Exception)
        conditions.eventually {
            assert registry.get("timed.lifecycle.flux.slow").tags("exception", "CancellationException").timer().count() == 1
        }
    }
}
