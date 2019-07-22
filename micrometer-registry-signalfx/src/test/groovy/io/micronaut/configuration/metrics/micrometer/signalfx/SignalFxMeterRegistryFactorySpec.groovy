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
package io.micronaut.configuration.metrics.micrometer.signalfx

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.signalfx.SignalFxMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.signalfx.SignalFxMeterRegistryFactory.SIGNALFX_CONFIG
import static io.micronaut.configuration.metrics.micrometer.signalfx.SignalFxMeterRegistryFactory.SIGNALFX_ENABLED

class SignalFxMeterRegistryFactorySpec extends Specification {

    private static String MOCK_SIGNALFX_ACCESS_TOKEN = "signalfxAccessToken"
    private static String MOCK_SIGNALFX_API_HOST = "http://somewhere/"

    void "verify SignalFxMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (SIGNALFX_CONFIG + ".accessToken")   : MOCK_SIGNALFX_ACCESS_TOKEN,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'SignalFxMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (SIGNALFX_CONFIG + ".accessToken")   : MOCK_SIGNALFX_ACCESS_TOKEN,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(SignalFxMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([SignalFxMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify SignalFxMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
                (SIGNALFX_CONFIG + ".accessToken")   : MOCK_SIGNALFX_ACCESS_TOKEN,
        ])

        then:
        context.findBean(SignalFxMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        SIGNALFX_ENABLED          | true    | true
        SIGNALFX_ENABLED          | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (SIGNALFX_ENABLED)           : true,
                (SIGNALFX_CONFIG + ".accessToken")   : MOCK_SIGNALFX_ACCESS_TOKEN,
        ])
        Optional<SignalFxMeterRegistry> signalFxMeterRegistry = context.findBean(SignalFxMeterRegistry)

        then: "default properties are used"
        signalFxMeterRegistry.isPresent()
        signalFxMeterRegistry.get().config.enabled()
        signalFxMeterRegistry.get().config.numThreads() == 2
        signalFxMeterRegistry.get().config.uri() == 'https://ingest.signalfx.com'
        signalFxMeterRegistry.get().config.step() == Duration.ofSeconds(10)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (SIGNALFX_ENABLED)                  : true,
                (SIGNALFX_CONFIG + ".numThreads")   : "77",
                (SIGNALFX_CONFIG + ".apiHost")      : MOCK_SIGNALFX_API_HOST,
                (SIGNALFX_CONFIG + ".accessToken")  : MOCK_SIGNALFX_ACCESS_TOKEN,
                (SIGNALFX_CONFIG + ".step")         : "PT2M",
        ])
        Optional<SignalFxMeterRegistry> signalFxMeterRegistry = context.findBean(SignalFxMeterRegistry)

        then:
        signalFxMeterRegistry.isPresent()
        signalFxMeterRegistry.get().config.enabled()
        signalFxMeterRegistry.get().config.numThreads() == 77
        signalFxMeterRegistry.get().config.uri() == MOCK_SIGNALFX_API_HOST
        signalFxMeterRegistry.get().config.accessToken() == MOCK_SIGNALFX_ACCESS_TOKEN
        signalFxMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}