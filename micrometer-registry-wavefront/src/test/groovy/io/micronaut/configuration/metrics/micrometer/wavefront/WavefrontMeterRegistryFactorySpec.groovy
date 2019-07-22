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
package io.micronaut.configuration.metrics.micrometer.wavefront

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.wavefront.WavefrontMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.wavefront.WavefrontMeterRegistryFactory.WAVEFRONT_CONFIG
import static io.micronaut.configuration.metrics.micrometer.wavefront.WavefrontMeterRegistryFactory.WAVEFRONT_ENABLED

class WavefrontMeterRegistryFactorySpec extends Specification {

    private static String MOCK_WAVEFRONT_API_TOKEN = "wavefrontApiToken"
    private static String MOCK_WAVEFRONT_API_URI = "http://somewhere/"

    void "verify WavefrontMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (WAVEFRONT_CONFIG + ".apiToken")  : MOCK_WAVEFRONT_API_TOKEN,
                (WAVEFRONT_CONFIG + ".uri")       : MOCK_WAVEFRONT_API_URI
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'WavefrontMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (WAVEFRONT_CONFIG + ".apiToken")  : MOCK_WAVEFRONT_API_TOKEN,
                (WAVEFRONT_CONFIG + ".uri")       : MOCK_WAVEFRONT_API_URI
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(WavefrontMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([WavefrontMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify WavefrontMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
                (WAVEFRONT_CONFIG + ".apiToken")  : MOCK_WAVEFRONT_API_TOKEN,
                (WAVEFRONT_CONFIG + ".uri")       : MOCK_WAVEFRONT_API_URI
        ])

        then:
        context.findBean(WavefrontMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        WAVEFRONT_ENABLED         | true    | true
        WAVEFRONT_ENABLED         | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (WAVEFRONT_ENABLED)           : true,
                (WAVEFRONT_CONFIG + ".apiToken")  : MOCK_WAVEFRONT_API_TOKEN,
                (WAVEFRONT_CONFIG + ".uri")       : 'https://longboard.wavefront.com'
        ])
        Optional<WavefrontMeterRegistry> wavefrontMeterRegistry = context.findBean(WavefrontMeterRegistry)

        then: "default properties are used"
        wavefrontMeterRegistry.isPresent()
        wavefrontMeterRegistry.get().config.enabled()
        wavefrontMeterRegistry.get().config.numThreads() == 2
        wavefrontMeterRegistry.get().config.uri() == 'https://longboard.wavefront.com'
        wavefrontMeterRegistry.get().config.step() == Duration.ofSeconds(10)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (WAVEFRONT_ENABLED)               : true,
                (WAVEFRONT_CONFIG + ".numThreads"): "77",
                (WAVEFRONT_CONFIG + ".uri")       : MOCK_WAVEFRONT_API_URI,
                (WAVEFRONT_CONFIG + ".apiToken")  : MOCK_WAVEFRONT_API_TOKEN,
                (WAVEFRONT_CONFIG + ".step")      : "PT2M",
        ])
        Optional<WavefrontMeterRegistry> wavefrontMeterRegistry = context.findBean(WavefrontMeterRegistry)

        then:
        wavefrontMeterRegistry.isPresent()
        wavefrontMeterRegistry.get().config.enabled()
        wavefrontMeterRegistry.get().config.numThreads() == 77
        wavefrontMeterRegistry.get().config.uri() == MOCK_WAVEFRONT_API_URI
        wavefrontMeterRegistry.get().config.apiToken() == MOCK_WAVEFRONT_API_TOKEN
        wavefrontMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}