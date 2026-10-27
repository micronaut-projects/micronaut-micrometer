package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * A false {@link MetricOptions#condition()} is evaluated before the other annotation values, so
 * their expressions are not evaluated for an invocation whose metrics are disabled.
 */
class ConditionExpressionSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()
    ConditionExpressionTarget target = ctx.getBean(ConditionExpressionTarget)
    MetricConditionProbe probe = ctx.getBean(MetricConditionProbe)
    MeterRegistry registry = ctx.getBean(MeterRegistry)

    void "the tag expressions of #name are not evaluated while the condition is false"() {
        when: 'the first invocation is disabled'
        def first = call(target)

        then: 'the tag expression is not evaluated and nothing is recorded'
        first == result
        registry.find(name).meters().empty

        when: 'an invocation is enabled'
        probe.enabled = true
        def enabled = call(target)

        then:
        enabled == result
        registry.find(name).tag('probe', 'ok').meters().size() == 1

        when: 'an invocation of the cached method is disabled'
        probe.enabled = false
        def disabled = call(target)

        then:
        disabled == result
        count(name) == 1

        where:
        name                           | result           | call
        'timed.condition.expression'   | 'timed-result'   | { ConditionExpressionTarget t -> t.timed() }
        'counted.condition.expression' | 'counted-result' | { ConditionExpressionTarget t -> t.counted() }
    }

    private long count(String name) {
        def timer = registry.find(name).timer()
        timer != null ? timer.count() : (long) registry.get(name).counter().count()
    }
}
