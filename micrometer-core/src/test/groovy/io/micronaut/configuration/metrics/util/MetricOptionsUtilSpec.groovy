package io.micronaut.configuration.metrics.util

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.configuration.metrics.annotation.MetricOptions
import spock.lang.Specification

class MetricOptionsUtilSpec extends Specification {

    void "returns true when condition is not present"() {
        given:
        MethodInvocationContext<?, ?> context = Mock() {
            1 * isPresent(MetricOptions, MetricOptions.MEMBER_CONDITION) >> false
            0 * _
        }

        expect:
        MetricOptionsUtil.evaluateCondition(context)
    }

    void "returns false when condition is present but does not resolve"() {
        given:
        MethodInvocationContext<?, ?> context = Mock() {
            1 * isPresent(MetricOptions, MetricOptions.MEMBER_CONDITION) >> true
            1 * booleanValue(MetricOptions, MetricOptions.MEMBER_CONDITION) >> Optional.empty()
            0 * _
        }

        expect:
        !MetricOptionsUtil.evaluateCondition(context)
    }

    void "returns evaluated condition"() {
        given:
        MethodInvocationContext<?, ?> context = Mock() {
            1 * isPresent(MetricOptions, MetricOptions.MEMBER_CONDITION) >> true
            1 * booleanValue(MetricOptions, MetricOptions.MEMBER_CONDITION) >> Optional.of(true)
            0 * _
        }

        expect:
        MetricOptionsUtil.evaluateCondition(context)
    }
}
