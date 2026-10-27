package io.micronaut.configuration.metrics.annotation

import io.micrometer.common.annotation.ValueExpressionResolver
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MeterTagSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'MeterTagSpec'])
    MeterTagTarget target = ctx.getBean(MeterTagTarget)
    MeterRegistry registry = ctx.getBean(MeterRegistry)
    PollingConditions conditions = new PollingConditions(timeout: 5)

    void "parameter tags use toString, a resolver bean, an instantiated resolver and evaluated expressions"() {
        when:
        target.parameters("acme", "upper", "four", "abc", null)

        then:
        def timer = registry.get("metertag.timed.parameters").timer()
        timer.count() == 1
        timer.id.getTag("tenant") == "acme"
        timer.id.getTag("upper") == "UPPER"
        timer.id.getTag("length") == "4"
        timer.id.getTag("zone") == "zone-a"
        timer.id.getTag("missing") == ""
        // a method level #{...} expression is evaluated against the arguments
        timer.id.getTag("size") == "4"
        timer.id.getTag("exception") == "none"
    }

    void "the tags are evaluated for each invocation"() {
        when:
        target.parameters("acme", "a", "b", "abc", null)
        target.parameters("other", "a", "b", "abcdef", null)

        then:
        registry.get("metertag.timed.parameters").tags("tenant", "acme", "size", "4").timer().count() == 1
        registry.get("metertag.timed.parameters").tags("tenant", "other", "size", "7").timer().count() == 1
    }

    void "a plain expression is resolved by the ValueExpressionResolver bean"() {
        when:
        target.expressionResolver("abc")

        then:
        registry.get("metertag.timed.expression.resolver").tag("reversed", "cba").timer().count() == 1
    }

    void "repeated tags on a parameter are all applied and the first tag with a key wins"() {
        when:
        target.repeated("value")

        then:
        def timer = registry.get("metertag.timed.repeated").timer()
        timer.id.getTag("first") == "value"
        timer.id.getTag("second") == "VALUE"
    }

    void "a method tag uses the result, which is empty when the method fails"() {
        when:
        target.result(5)
        target.result(-1)

        then:
        thrown(IllegalArgumentException)
        registry.get("metertag.timed.result").tags("result", "5", "exception", "none").timer().count() == 1
        registry.get("metertag.timed.result").tags("result", "", "exception", "IllegalArgumentException").timer().count() == 1
    }

    void "counted methods are tagged from the parameters and the result"() {
        when:
        target.counted("acme")
        target.countedMono("acme").block()

        then:
        registry.get("metertag.counted").tags("tenant", "acme", "returned", "OK-ACME", "result", "success").counter().count() == 1
        registry.get("metertag.counted.mono").tags("tenant", "acme", "returned", "mono-acme", "result", "success").counter().count() == 1
    }

    void "the result tag of a CompletionStage method uses the completed value"() {
        when:
        target.timedFuture("acme").get()

        then:
        conditions.eventually {
            assert registry.get("metertag.timed.future").tags("tenant", "acme", "result", "future-acme").timer().count() == 1
        }
    }

    void "long task timers are tagged from the parameters"() {
        given:
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)

        when:
        def result = CompletableFuture.supplyAsync { target.longTask("acme", started, release) }
        started.await(5, TimeUnit.SECONDS)

        then:
        registry.get("metertag.long.task").tag("tenant", "acme").longTaskTimer().activeTasks() == 1

        cleanup:
        release.countDown()
        result.get(5, TimeUnit.SECONDS)
    }

    void "a tag declared on an interface parameter is applied"() {
        when:
        target.fromInterface("eu")

        then:
        registry.get("metertag.timed.interface").tag("region", "eu").timer().count() == 1
    }

    void "a plain expression without a ValueExpressionResolver bean resolves to an empty value"() {
        given:
        ApplicationContext other = ApplicationContext.run()

        when:
        other.getBean(MeterTagTarget).expressionResolver("abc")

        then:
        other.getBean(MeterRegistry).get("metertag.timed.expression.resolver").tag("reversed", "").timer().count() == 1

        cleanup:
        other.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "MeterTagSpec")
    static class ReversingExpressionResolver implements ValueExpressionResolver {
        @Override
        String resolve(String expression, Object parameter) {
            assert expression == "reverse"
            return parameter.toString().reverse()
        }
    }
}
