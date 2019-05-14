/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.metrics.micrometer.elastic

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.elastic.ElasticMeterRegistry
import io.micronaut.configuration.metrics.micrometer.MeterRegistryCreationListener
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.elastic.ElasticMeterRegistryFactory.ELASTIC_CONFIG
import static io.micronaut.configuration.metrics.micrometer.elastic.ElasticMeterRegistryFactory.ELASTIC_ENABLED

class ElasticMeterRegistryFactorySpec extends Specification {

    void "wireup the bean manually"() {
        setup:
        Environment mockEnvironment = Stub()
        mockEnvironment.getProperty(_, _) >> Optional.empty()

        when:
        ElasticMeterRegistryFactory factory = new ElasticMeterRegistryFactory(new ElasticConfigurationProperties(mockEnvironment))

        then:
        factory.elasticConfig()
    }

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
        context.getBean(MeterRegistryCreationListener)
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
        ELASTIC_ENABLED             | true    | true
        ELASTIC_ENABLED             | false   | false
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
        elasticMeterRegistry.get().config.numThreads() == 2
        elasticMeterRegistry.get().config.index() == 'metrics'
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
                (ELASTIC_CONFIG + ".host")       : "http://somewhere/",
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
