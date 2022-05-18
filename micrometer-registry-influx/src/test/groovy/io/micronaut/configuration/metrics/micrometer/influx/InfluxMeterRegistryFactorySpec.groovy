package io.micronaut.configuration.metrics.micrometer.influx

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.influx.InfluxConsistency
import io.micrometer.influx.InfluxMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.influx.InfluxMeterRegistryFactory.INFLUX_CONFIG
import static io.micronaut.configuration.metrics.micrometer.influx.InfluxMeterRegistryFactory.INFLUX_ENABLED

class InfluxMeterRegistryFactorySpec extends Specification {

    void "verify InfluxMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'InfluxMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(InfluxMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([InfluxMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify InfluxMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(InfluxMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        INFLUX_ENABLED             | true    | true
        INFLUX_ENABLED             | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (INFLUX_ENABLED): true,
        ])
        Optional<InfluxMeterRegistry> influxMeterRegistry = context.findBean(InfluxMeterRegistry)

        then: "default properties are used"
        influxMeterRegistry.isPresent()
        influxMeterRegistry.get().config.enabled()
        influxMeterRegistry.get().config.numThreads() == 2
        influxMeterRegistry.get().config.db() == 'mydb'
        influxMeterRegistry.get().config.uri() == 'http://localhost:8086'
        influxMeterRegistry.get().config.consistency() == InfluxConsistency.ONE
        influxMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (INFLUX_ENABLED)               : true,
                (INFLUX_CONFIG + ".numThreads"): "77",
                (INFLUX_CONFIG + ".uri")       : "http://somewhere/",
                (INFLUX_CONFIG + ".step")      : "PT2M",
        ])
        Optional<InfluxMeterRegistry> influxMeterRegistry = context.findBean(InfluxMeterRegistry)

        then:
        influxMeterRegistry.isPresent()
        influxMeterRegistry.get().config.enabled()
        influxMeterRegistry.get().config.numThreads() == 77
        influxMeterRegistry.get().config.uri() == "http://somewhere/"
        influxMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
