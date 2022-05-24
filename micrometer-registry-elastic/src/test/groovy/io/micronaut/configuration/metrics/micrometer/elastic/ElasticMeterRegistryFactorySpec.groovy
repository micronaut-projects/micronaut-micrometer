package io.micronaut.configuration.metrics.micrometer.elastic

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.elastic.ElasticMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.elastic.ElasticMeterRegistryFactory.ELASTIC_CONFIG
import static io.micronaut.configuration.metrics.micrometer.elastic.ElasticMeterRegistryFactory.ELASTIC_ENABLED

class ElasticMeterRegistryFactorySpec extends Specification {

    void "verify ElasticMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'ElasticMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(ElasticMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([ElasticMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify ElasticMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(ElasticMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        ELASTIC_ENABLED           | true    | true
        ELASTIC_ENABLED           | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (ELASTIC_ENABLED): true,
        ])
        Optional<ElasticMeterRegistry> elasticMeterRegistry = context.findBean(ElasticMeterRegistry)

        then: "default properties are used"
        elasticMeterRegistry.isPresent()
        elasticMeterRegistry.get().config.enabled()
        elasticMeterRegistry.get().config.index() == 'micrometer-metrics'
        elasticMeterRegistry.get().config.host() == 'http://localhost:9200'
        elasticMeterRegistry.get().config.indexDateFormat() == 'yyyy-MM'
        elasticMeterRegistry.get().config.timestampFieldName() == '@timestamp'
        elasticMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (ELASTIC_ENABLED)               : true,
                (ELASTIC_CONFIG + ".numThreads"): "77",
                (ELASTIC_CONFIG + ".host")      : "http://somewhere/",
                (ELASTIC_CONFIG + ".step")      : "PT2M",
        ])
        Optional<ElasticMeterRegistry> elasticMeterRegistry = context.findBean(ElasticMeterRegistry)

        then:
        elasticMeterRegistry.isPresent()
        elasticMeterRegistry.get().config.enabled()
        elasticMeterRegistry.get().config.numThreads() == 77
        elasticMeterRegistry.get().config.host() == "http://somewhere/"
        elasticMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
