package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

/**
 * Two first invocations of a method with annotation expressions that both resolve its annotations
 * before either cached them each record their own evaluated values.
 */
class ConcurrentExpressionSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()
    ConcurrentExpressionTarget target = ctx.getBean(ConcurrentExpressionTarget)
    MeterRegistry registry = ctx.getBean(MeterRegistry)

    void "concurrent first invocations of #name record their own evaluated tags"() {
        given:
        CyclicBarrier barrier = new CyclicBarrier(2)

        when:
        List<String> results = ['alpha', 'beta'].collect { String arg ->
            CompletableFuture.supplyAsync {
                FirstCallBarrier.arm(barrier)
                call.apply(target, arg)
            }
        }*.get(10, TimeUnit.SECONDS)

        then:
        results == ['alpha', 'beta']
        count(name, 'alpha') == 1
        count(name, 'beta') == 1

        when: 'the cached method is invoked again'
        call.apply(target, 'gamma')

        then:
        count(name, 'gamma') == 1

        where:
        name                           | call
        'timed.concurrent.expression'   | ({ ConcurrentExpressionTarget t, String arg -> t.timed(arg) } as BiFunction)
        'counted.concurrent.expression' | ({ ConcurrentExpressionTarget t, String arg -> t.counted(arg) } as BiFunction)
    }

    private long count(String name, String arg) {
        def timer = registry.find(name).tag('arg', arg).timer()
        if (timer != null) {
            return timer.count()
        }
        def counter = registry.find(name).tag('arg', arg).counter()
        counter == null ? 0 : (long) counter.count()
    }
}
