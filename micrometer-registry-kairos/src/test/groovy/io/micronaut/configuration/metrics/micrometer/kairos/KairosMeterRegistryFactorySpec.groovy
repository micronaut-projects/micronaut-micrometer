/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.micrometer.kairos

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.kairos.KairosMeterRegistry
import io.micronaut.configuration.metrics.micrometer.MeterRegistryCreationListener
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.kairos.KairosMeterRegistryFactory.KAIROS_CONFIG
import static io.micronaut.configuration.metrics.micrometer.kairos.KairosMeterRegistryFactory.KAIROS_ENABLED

class KairosMeterRegistryFactorySpec extends Specification {

    void "wireup the bean manually"() {
        setup:
        ApplicationContext context = ApplicationContext.run()
        Environment mockEnvironment = context.getEnvironment()

        when:
        KairosMeterRegistryFactory factory = new KairosMeterRegistryFactory(new KairosConfigurationProperties(mockEnvironment))

        then:
        factory.kairosMeterRegistry()
    }

    void "verify KairosMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'KairosMeterRegistry'])

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
        context.getBean(KairosMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([KairosMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify KairosMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
        ])

        then:
        context.findBean(KairosMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        KAIROS_ENABLED          | true    | true
        KAIROS_ENABLED          | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (KAIROS_ENABLED)           : true,
        ])
        Optional<KairosMeterRegistry> kairosMeterRegistry = context.findBean(KairosMeterRegistry)

        then: "default properties are used"
        kairosMeterRegistry.isPresent()
        kairosMeterRegistry.get().config.enabled()
        kairosMeterRegistry.get().config.numThreads() == 2
        kairosMeterRegistry.get().config.uri() == 'http://localhost:8080/api/v1/datapoints'
        kairosMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (KAIROS_ENABLED)                   : true,
                (KAIROS_CONFIG + ".numThreads")    : "77",
                (KAIROS_CONFIG + ".uri")           : 'https://micronaut.io',
                (KAIROS_CONFIG + ".step")          : "PT2M",
        ])
        Optional<KairosMeterRegistry> kairosMeterRegistry = context.findBean(KairosMeterRegistry)

        then:
        kairosMeterRegistry.isPresent()
        kairosMeterRegistry.get().config.enabled()
        kairosMeterRegistry.get().config.numThreads() == 77
        kairosMeterRegistry.get().config.uri() == 'https://micronaut.io'
        kairosMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
