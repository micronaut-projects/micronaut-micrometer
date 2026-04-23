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

    void "returns evaluated condition and supports debug logging"() {
        given:
        def logger = loggerForMetricOptionsUtil()
        def originalLevel = logger?.level
        def debugLevel = resolveDebugLevel()
        if (logger != null && debugLevel != null) {
            logger.setLevel(debugLevel)
        }
        MethodInvocationContext<?, ?> context = Mock() {
            1 * isPresent(MetricOptions, MetricOptions.MEMBER_CONDITION) >> true
            1 * booleanValue(MetricOptions, MetricOptions.MEMBER_CONDITION) >> Optional.of(true)
            _ * toString() >> "testInvocation"
        }

        expect:
        MetricOptionsUtil.evaluateCondition(context)

        cleanup:
        if (logger != null) {
            logger.setLevel(originalLevel)
        }
    }

    private static Object loggerForMetricOptionsUtil() {
        try {
            def loggerFactory = org.slf4j.LoggerFactory.getILoggerFactory()
            return loggerFactory.class.getMethod("getLogger", String).invoke(loggerFactory, MetricOptionsUtil.name)
        } catch (ReflectiveOperationException ignored) {
            return null
        }
    }

    private static Object resolveDebugLevel() {
        try {
            return Class.forName("ch.qos.logback.classic.Level").getField("DEBUG").get(null)
        } catch (ReflectiveOperationException ignored) {
            return null
        }
    }
}
