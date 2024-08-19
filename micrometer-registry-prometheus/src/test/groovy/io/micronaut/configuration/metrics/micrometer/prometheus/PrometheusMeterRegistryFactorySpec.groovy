package io.micronaut.configuration.metrics.micrometer.prometheus

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static PrometheusMeterRegistryFactory.PROMETHEUS_CONFIG
import static PrometheusMeterRegistryFactory.PROMETHEUS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class PrometheusMeterRegistryFactorySpec extends Specification {

    void "verify PrometheusMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'PrometheusMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(PrometheusMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([PrometheusMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify PrometheusMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(PrometheusMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        PROMETHEUS_ENABLED        | true    | true
        PROMETHEUS_ENABLED        | false   | false
    }

    void "verify default configuration"() {
        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (PROMETHEUS_ENABLED): true,
        ])
        Optional<PrometheusMeterRegistry> prometheusMeterRegistry = context.findBean(PrometheusMeterRegistry)

        then: "default properties are used"
        prometheusMeterRegistry.isPresent()
        prometheusMeterRegistry.get().prometheusConfig.descriptions()
        prometheusMeterRegistry.get().prometheusConfig.prefix() == "prometheus"
        prometheusMeterRegistry.get().prometheusConfig.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {
        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (PROMETHEUS_ENABLED)                 : true,
                (PROMETHEUS_CONFIG + ".descriptions"): false,
                (PROMETHEUS_CONFIG + ".prefix")      : "ppp",
                (PROMETHEUS_CONFIG + ".step")        : "PT2M",
        ])
        Optional<PrometheusMeterRegistry> prometheusMeterRegistry = context.findBean(PrometheusMeterRegistry)

        then:
        prometheusMeterRegistry.isPresent()
        !prometheusMeterRegistry.get().prometheusConfig.descriptions()
        prometheusMeterRegistry.get().prometheusConfig.step() == Duration.ofMinutes(2)

        and: 'Prefix is hard coded in base library...???'
        prometheusMeterRegistry.get().prometheusConfig.prefix() == "prometheus"

        cleanup:
        context.stop()
    }
}
