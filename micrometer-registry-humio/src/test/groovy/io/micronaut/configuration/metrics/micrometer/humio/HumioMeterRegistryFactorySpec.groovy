package io.micronaut.configuration.metrics.micrometer.humio

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.humio.HumioMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.humio.HumioMeterRegistryFactory.HUMIO_CONFIG
import static io.micronaut.configuration.metrics.micrometer.humio.HumioMeterRegistryFactory.HUMIO_ENABLED

class HumioMeterRegistryFactorySpec extends Specification {

    void "verify HumioMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'HumioMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(HumioMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([HumioMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify HumioMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
        ])

        then:
        context.findBean(HumioMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        HUMIO_ENABLED             | true    | true
        HUMIO_ENABLED             | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (HUMIO_ENABLED): true,
        ])
        Optional<HumioMeterRegistry> humioMeterRegistry = context.findBean(HumioMeterRegistry)

        then: "default properties are used"
        humioMeterRegistry.isPresent()
        humioMeterRegistry.get().config.enabled()
        humioMeterRegistry.get().config.uri() == 'https://cloud.humio.com'
        humioMeterRegistry.get().config.numThreads() == 2
        humioMeterRegistry.get().config.batchSize() == 10000
        humioMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (HUMIO_ENABLED)               : true,
                (HUMIO_CONFIG + ".numThreads"): "77",
                (HUMIO_CONFIG + ".uri")       : 'https://micronaut.io',
                (HUMIO_CONFIG + ".repository"): 'micronaut',
                (HUMIO_CONFIG + ".step")      : "PT2M",
        ])
        Optional<HumioMeterRegistry> humioMeterRegistry = context.findBean(HumioMeterRegistry)

        then:
        humioMeterRegistry.isPresent()
        humioMeterRegistry.get().config.enabled()
        humioMeterRegistry.get().config.numThreads() == 77
        humioMeterRegistry.get().config.uri() == 'https://micronaut.io'
        humioMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
