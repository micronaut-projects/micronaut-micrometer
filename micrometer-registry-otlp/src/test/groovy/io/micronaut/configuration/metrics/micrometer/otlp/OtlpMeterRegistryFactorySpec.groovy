package io.micronaut.configuration.metrics.micrometer.otlp

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.otlp.OtlpMeterRegistryFactory.OTLP_ENABLED

class OtlpMeterRegistryFactorySpec extends Specification {

    void "verify OtlpMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'OtlpMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
        compositeRegistry
        context.getBean(OtlpMeterRegistry)
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([OtlpMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify OtlpMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(OtlpMeterRegistry).isPresent() == result

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        OTLP_ENABLED              | true    | true
        OTLP_ENABLED              | false   | false
    }
}
