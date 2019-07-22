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
package io.micronaut.configuration.metrics.micrometer.datadog

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.datadog.DatadogMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.datadog.DatadogMeterRegistryFactory.DATADOG_CONFIG
import static io.micronaut.configuration.metrics.micrometer.datadog.DatadogMeterRegistryFactory.DATADOG_ENABLED

class DatadogMeterRegistryFactorySpec extends Specification {

    void "verify DatadogMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (DATADOG_CONFIG + ".apiKey")    : "12345"
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'DatadogMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (DATADOG_CONFIG + ".apiKey")    : "12345"
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(DatadogMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([DatadogMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify DatadogMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
                (DATADOG_CONFIG + ".apiKey")    : "12345"
        ])

        then:
        context.findBean(DatadogMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        DATADOG_ENABLED           | true    | true
        DATADOG_ENABLED           | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (DATADOG_ENABLED)           : true,
                (DATADOG_CONFIG + ".apiKey"): "12345",
        ])
        Optional<DatadogMeterRegistry> datadogMeterRegistry = context.findBean(DatadogMeterRegistry)

        then: "default properties are used"
        datadogMeterRegistry.isPresent()
        datadogMeterRegistry.get().config.enabled()
        datadogMeterRegistry.get().config.numThreads() == 2
        datadogMeterRegistry.get().config.hostTag() == 'instance'
        datadogMeterRegistry.get().config.uri() == 'https://app.datadoghq.com'
        datadogMeterRegistry.get().config.apiKey() == '12345'
        datadogMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (DATADOG_ENABLED)               : true,
                (DATADOG_CONFIG + ".numThreads"): "77",
                (DATADOG_CONFIG + ".uri")       : "http://somewhere/",
                (DATADOG_CONFIG + ".apiKey")    : "tempKey",
                (DATADOG_CONFIG + ".step")      : "PT2M",
        ])
        Optional<DatadogMeterRegistry> datadogMeterRegistry = context.findBean(DatadogMeterRegistry)

        then:
        datadogMeterRegistry.isPresent()
        datadogMeterRegistry.get().config.enabled()
        datadogMeterRegistry.get().config.numThreads() == 77
        datadogMeterRegistry.get().config.uri() == "http://somewhere/"
        datadogMeterRegistry.get().config.apiKey() == 'tempKey'
        datadogMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
