package io.micronaut.configuration.metrics.micrometer.jmx

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.jmx.JmxMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.jmx.JmxMeterRegistryFactory.JMX_ENABLED

class JmxMeterRegistryFactorySpec extends Specification {

    void "verify JmxMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'JmxMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(JmxMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([JmxMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify JmxMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
        ])

        then:
        context.findBean(JmxMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        JMX_ENABLED               | true    | true
        JMX_ENABLED               | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (JMX_ENABLED): true,
        ])
        Optional<JmxMeterRegistry> jmxMeterRegistry = context.findBean(JmxMeterRegistry)

        then: "default properties are used"
        jmxMeterRegistry.isPresent()

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (JMX_ENABLED): true,
        ])
        Optional<JmxMeterRegistry> jmxMeterRegistry = context.findBean(JmxMeterRegistry)

        then:
        jmxMeterRegistry.isPresent()

        cleanup:
        context.stop()
    }
}
