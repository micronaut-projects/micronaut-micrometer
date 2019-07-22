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
package io.micronaut.configuration.metrics.micrometer.dynatrace

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.dynatrace.DynatraceMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.dynatrace.DynatraceMeterRegistryFactory.DYNATRACE_CONFIG
import static io.micronaut.configuration.metrics.micrometer.dynatrace.DynatraceMeterRegistryFactory.DYNATRACE_ENABLED

class DynatraceRegistryFactorySpec extends Specification {

    private static final String DYNATRACE_MOCK_API_TOKEN = "dynatraceApiToken"
    private static final String DYNATRACE_MOCK_URI = "https://mock-api-tocken"
    private static final String DYNATRACE_MOCK_DEVICE_ID = "dynatraceDeviceId"

    void "verify DynatraceMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_CONFIG + ".apiToken")    : DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")         : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId")    : DYNATRACE_MOCK_DEVICE_ID,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'DynatraceMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_CONFIG + ".apiToken")    : DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")         : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId")    : DYNATRACE_MOCK_DEVICE_ID,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(DynatraceMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([DynatraceMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify DynatraceMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
                (DYNATRACE_CONFIG + ".apiToken")    : DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")         : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId")    : DYNATRACE_MOCK_DEVICE_ID,

        ])

        then:
        context.findBean(DynatraceMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                        | setting | result
        MICRONAUT_METRICS_ENABLED  | false   | false
        MICRONAUT_METRICS_ENABLED  | true    | true
        DYNATRACE_ENABLED          | true    | true
        DYNATRACE_ENABLED          | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_ENABLED)           : true,
                (DYNATRACE_CONFIG + ".apiToken")    : DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")         : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId")    : DYNATRACE_MOCK_DEVICE_ID,
        ])
        Optional<DynatraceMeterRegistry> dynatraceMeterRegistry = context.findBean(DynatraceMeterRegistry)

        then: "default properties are used"
        dynatraceMeterRegistry.isPresent()
        dynatraceMeterRegistry.get().config.enabled()
        dynatraceMeterRegistry.get().config.numThreads() == 2
        dynatraceMeterRegistry.get().config.uri() == DYNATRACE_MOCK_URI
        dynatraceMeterRegistry.get().config.apiToken() == DYNATRACE_MOCK_API_TOKEN
        dynatraceMeterRegistry.get().config.deviceId() == DYNATRACE_MOCK_DEVICE_ID
        dynatraceMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_ENABLED)                   : true,
                (DYNATRACE_CONFIG + ".numThreads")    : "77",
                (DYNATRACE_CONFIG + ".uri")           : 'https://micronaut.io',
                (DYNATRACE_CONFIG + ".step")          : "PT2M",
                (DYNATRACE_CONFIG + ".apiToken")      : DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".deviceId")      : DYNATRACE_MOCK_DEVICE_ID,
        ])
        Optional<DynatraceMeterRegistry> dynatraceMeterRegistry = context.findBean(DynatraceMeterRegistry)

        then:
        dynatraceMeterRegistry.isPresent()
        dynatraceMeterRegistry.get().config.enabled()
        dynatraceMeterRegistry.get().config.numThreads() == 77
        dynatraceMeterRegistry.get().config.apiToken() == DYNATRACE_MOCK_API_TOKEN
        dynatraceMeterRegistry.get().config.deviceId() == DYNATRACE_MOCK_DEVICE_ID
        dynatraceMeterRegistry.get().config.uri() == 'https://micronaut.io'
        dynatraceMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
