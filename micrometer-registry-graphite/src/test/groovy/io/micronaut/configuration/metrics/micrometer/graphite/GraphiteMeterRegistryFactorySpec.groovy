package io.micronaut.configuration.metrics.micrometer.graphite

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.graphite.GraphiteConfig
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteMeterRegistryFactory.GRAPHITE_CONFIG
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteMeterRegistryFactory.GRAPHITE_ENABLED
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteMeterRegistryFactory.GRAPHITE_TAGS_AS_PREFIX

class GraphiteMeterRegistryFactorySpec extends Specification {

    void "verify GraphiteMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'GraphiteMeterRegistry'])
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
        context.getBean(GraphiteMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([GraphiteMeterRegistry])
    }

    @Unroll
    void "verify GraphiteMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(GraphiteMeterRegistry).isPresent() == result

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        GRAPHITE_ENABLED          | true    | true
        GRAPHITE_ENABLED          | false   | false
    }

    void "verify GraphiteMeterRegistry bean exists with default config"() {
        when:
        ApplicationContext context = ApplicationContext.run([(GRAPHITE_ENABLED): true])
        Optional<GraphiteMeterRegistry> meterRegistry = context.findBean(GraphiteMeterRegistry)

        then:
        meterRegistry.isPresent()
        meterRegistry.get().config.enabled()
        meterRegistry.get().config.port() == 2004
        meterRegistry.get().config.host() == "localhost"
        meterRegistry.get().config.step() == Duration.ofMinutes(1)
    }

    void "verify GraphiteMeterRegistry bean exists changed port, host and step"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (GRAPHITE_ENABLED)         : true,
                (GRAPHITE_CONFIG + ".host"): "127.0.0.1",
                (GRAPHITE_CONFIG + ".port"): 2345,
                (GRAPHITE_CONFIG + ".step"): "PT2M",
                (GRAPHITE_TAGS_AS_PREFIX)  : ['foo', 'bar']
        ])
        Optional<GraphiteMeterRegistry> meterRegistry = context.findBean(GraphiteMeterRegistry)
        def config = context.getBean(GraphiteConfig)
        then:
        config.tagsAsPrefix() == ['foo', 'bar'] as String[]
        meterRegistry.isPresent()
        meterRegistry.get().config.enabled()
        meterRegistry.get().config.port() == 2345
        meterRegistry.get().config.host() == "127.0.0.1"
        meterRegistry.get().config.step() == Duration.ofMinutes(2)
    }
}
