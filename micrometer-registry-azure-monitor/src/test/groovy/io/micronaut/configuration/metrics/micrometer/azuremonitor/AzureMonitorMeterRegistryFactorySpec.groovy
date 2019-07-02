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
package io.micronaut.configuration.metrics.micrometer.azuremonitor

import io.micrometer.azuremonitor.AzureMonitorMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.configuration.metrics.micrometer.MeterRegistryCreationListener
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.azuremonitor.AzureMonitorMeterRegistryFactory.AZUREMONITOR_CONFIG
import static io.micronaut.configuration.metrics.micrometer.azuremonitor.AzureMonitorMeterRegistryFactory.AZUREMONITOR_ENABLED

class AzureMonitorMeterRegistryFactorySpec extends Specification {

    private static String MOCK_AZURE_INSTRUMENTATION_KEY = "micronautInstrumentationKey"

    void "fail to create bean without required properties"() {
        when:
        ApplicationContext.run()

        then:
        thrown BeanInstantiationException
    }

    void "wireup the bean manually"() {
        setup:
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_CONFIG + ".instrumentationKey")    : MOCK_AZURE_INSTRUMENTATION_KEY,
        ])
        Environment mockEnvironment = context.getEnvironment()

        when:
        AzureMonitorMeterRegistryFactory factory = new AzureMonitorMeterRegistryFactory(new AzureMonitorConfigurationProperties(mockEnvironment))

        then:
        factory.azureMonitorMeterRegistry()
    }

    void "verify AzureMonitorMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_CONFIG + ".instrumentationKey")    : MOCK_AZURE_INSTRUMENTATION_KEY,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'AzureMonitorMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_CONFIG + ".instrumentationKey")    : MOCK_AZURE_INSTRUMENTATION_KEY,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(MeterRegistryCreationListener)
        context.getBean(AzureMonitorMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([AzureMonitorMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify AzureMonitorMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
                (AZUREMONITOR_CONFIG + ".instrumentationKey")    : MOCK_AZURE_INSTRUMENTATION_KEY,
        ])

        then:
        context.findBean(AzureMonitorMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        AZUREMONITOR_ENABLED      | true    | true
        AZUREMONITOR_ENABLED      | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_ENABLED)                 : true,
                (AZUREMONITOR_CONFIG + ".instrumentationKey")    : MOCK_AZURE_INSTRUMENTATION_KEY,
        ])
        Optional<AzureMonitorMeterRegistry> azureMonitorMeterRegistry = context.findBean(AzureMonitorMeterRegistry)

        then: "default properties are used"
        azureMonitorMeterRegistry.isPresent()
        azureMonitorMeterRegistry.get().config.enabled()
        azureMonitorMeterRegistry.get().config.numThreads() == 2
        azureMonitorMeterRegistry.get().config.batchSize() == 10000
        azureMonitorMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_ENABLED)                   : true,
                (AZUREMONITOR_CONFIG + ".instrumentationKey")    : MOCK_AZURE_INSTRUMENTATION_KEY,
                (AZUREMONITOR_CONFIG + ".numThreads")    : "77",
                (AZUREMONITOR_CONFIG + ".step")          : "PT2M",
        ])
        Optional<AzureMonitorMeterRegistry> azureMonitorMeterRegistry = context.findBean(AzureMonitorMeterRegistry)

        then:
        azureMonitorMeterRegistry.isPresent()
        azureMonitorMeterRegistry.get().config.enabled()
        azureMonitorMeterRegistry.get().config.numThreads() == 77
        azureMonitorMeterRegistry.get().config.instrumentationKey() == MOCK_AZURE_INSTRUMENTATION_KEY
        azureMonitorMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }


}
