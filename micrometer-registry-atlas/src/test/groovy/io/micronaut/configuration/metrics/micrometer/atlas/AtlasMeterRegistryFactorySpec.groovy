package io.micronaut.configuration.metrics.micrometer.atlas

import io.micrometer.atlas.AtlasMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.atlas.AtlasMeterRegistryFactory.ATLAS_CONFIG
import static io.micronaut.configuration.metrics.micrometer.atlas.AtlasMeterRegistryFactory.ATLAS_ENABLED

class AtlasMeterRegistryFactorySpec extends Specification {

    void "verify AtlasMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'AtlasMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(AtlasMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([AtlasMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify AtlasMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(AtlasMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        ATLAS_ENABLED             | true    | true
        ATLAS_ENABLED             | false   | false
    }

    void "verify default configuration"() {
        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (ATLAS_ENABLED): true,
        ])
        Optional<AtlasMeterRegistry> atlasMeterRegistry = context.findBean(AtlasMeterRegistry)

        then: "default properties are used"
        atlasMeterRegistry.isPresent()
        atlasMeterRegistry.get().atlasConfig.enabled()
        atlasMeterRegistry.get().atlasConfig.numThreads() == 4
        atlasMeterRegistry.get().atlasConfig.uri() == 'http://localhost:7101/api/v1/publish'
        atlasMeterRegistry.get().atlasConfig.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {
        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (ATLAS_ENABLED)               : true,
                (ATLAS_CONFIG + ".numThreads"): "77",
                (ATLAS_CONFIG + ".uri")       : "http://somewhere/",
                (ATLAS_CONFIG + ".step")      : "PT2M",
        ])
        Optional<AtlasMeterRegistry> atlasMeterRegistry = context.findBean(AtlasMeterRegistry)

        then:
        atlasMeterRegistry.isPresent()
        atlasMeterRegistry.get().atlasConfig.enabled()
        atlasMeterRegistry.get().atlasConfig.numThreads() == 77
        atlasMeterRegistry.get().atlasConfig.uri() == "http://somewhere/"
        atlasMeterRegistry.get().atlasConfig.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }
}
