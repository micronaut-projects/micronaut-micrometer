package io.micronaut.configuration.metrics.micrometer.ganglia

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.ganglia.GangliaMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.TimeUnit

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.ganglia.GangliaMeterRegistryFactory.GANGLIA_CONFIG
import static io.micronaut.configuration.metrics.micrometer.ganglia.GangliaMeterRegistryFactory.GANGLIA_ENABLED

class GangliaMeterRegistryFactorySpec extends Specification {

    void "verify GangliaMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'GangliaMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(GangliaMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([GangliaMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify GangliaMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
        ])

        then:
        context.findBean(GangliaMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        GANGLIA_ENABLED           | true    | true
        GANGLIA_ENABLED           | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (GANGLIA_ENABLED): true,
        ])
        Optional<GangliaMeterRegistry> gangliaMeterRegistry = context.findBean(GangliaMeterRegistry)

        then: "default properties are used"
        gangliaMeterRegistry.isPresent()
        gangliaMeterRegistry.get().config.enabled()
        gangliaMeterRegistry.get().config.batchSize() == 10000
        gangliaMeterRegistry.get().config.host() == 'localhost'
        gangliaMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (GANGLIA_ENABLED)                    : true,
                (GANGLIA_CONFIG + ".numThreads")     : "77",
                (GANGLIA_CONFIG + ".host")           : '127.0.0.1',
                (GANGLIA_CONFIG + ".step")           : "PT2M",
                (GANGLIA_CONFIG + ".rateUnits")      : TimeUnit.MILLISECONDS,
                (GANGLIA_CONFIG + ".durationUnits")  : TimeUnit.SECONDS,
                (GANGLIA_CONFIG + ".protocolVersion"): "3.0",
        ])
        Optional<GangliaMeterRegistry> gangliaMeterRegistry = context.findBean(GangliaMeterRegistry)

        then:
        gangliaMeterRegistry.isPresent()
        gangliaMeterRegistry.get().config.enabled()
        gangliaMeterRegistry.get().config.host() == '127.0.0.1'
        gangliaMeterRegistry.get().config.durationUnits() == TimeUnit.SECONDS
        gangliaMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
