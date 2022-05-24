package io.micronaut.configuration.metrics.micrometer.stackdriver

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.stackdriver.StackdriverMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.stackdriver.StackdriverMeterRegistryFactory.STACKDRIVER_CONFIG
import static io.micronaut.configuration.metrics.micrometer.stackdriver.StackdriverMeterRegistryFactory.STACKDRIVER_ENABLED

class StackdriverMeterRegistryFactorySpec extends Specification {

    private static String MOCK_WAVEFRONT_PROJECTID = "stackdriverProjectId"

    void "verify StackdriverMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'StackdriverMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(StackdriverMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([StackdriverMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify StackdriverMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg)                              : setting,
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])

        then:
        context.findBean(StackdriverMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        STACKDRIVER_ENABLED       | true    | true
        STACKDRIVER_ENABLED       | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_ENABLED)              : true,
                (STACKDRIVER_CONFIG + ".projectId"): MOCK_WAVEFRONT_PROJECTID,
        ])
        Optional<StackdriverMeterRegistry> stackdriverMeterRegistry = context.findBean(StackdriverMeterRegistry)

        then: "default properties are used"
        stackdriverMeterRegistry.isPresent()
        stackdriverMeterRegistry.get().config.enabled()
        stackdriverMeterRegistry.get().config.numThreads() == 2
        stackdriverMeterRegistry.get().config.projectId() == MOCK_WAVEFRONT_PROJECTID
        stackdriverMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (STACKDRIVER_ENABLED)               : true,
                (STACKDRIVER_CONFIG + ".numThreads"): "77",
                (STACKDRIVER_CONFIG + ".projectId") : MOCK_WAVEFRONT_PROJECTID,
                (STACKDRIVER_CONFIG + ".step")      : "PT2M",
        ])
        Optional<StackdriverMeterRegistry> stackdriverMeterRegistry = context.findBean(StackdriverMeterRegistry)

        then:
        stackdriverMeterRegistry.isPresent()
        stackdriverMeterRegistry.get().config.enabled()
        stackdriverMeterRegistry.get().config.numThreads() == 77
        stackdriverMeterRegistry.get().config.projectId() == MOCK_WAVEFRONT_PROJECTID
        stackdriverMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
