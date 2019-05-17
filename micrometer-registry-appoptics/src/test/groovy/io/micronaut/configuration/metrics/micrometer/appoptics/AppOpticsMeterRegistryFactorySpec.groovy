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

package io.micronaut.configuration.metrics.micrometer.appoptics

import io.micrometer.appoptics.AppOpticsMeterRegistry
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
import static io.micronaut.configuration.metrics.micrometer.appoptics.AppOpticsMeterRegistryFactory.APPOPTICS_CONFIG
import static io.micronaut.configuration.metrics.micrometer.appoptics.AppOpticsMeterRegistryFactory.APPOPTICS_ENABLED

class AppOpticsMeterRegistryFactorySpec extends Specification {

    private static String MOCK_APPOPTICS_API_TOKEN = "appoticsApiToken"

    void "fail to create bean without required properties"() {
        when:
        ApplicationContext.run()

        then:
        thrown BeanInstantiationException
    }

    void "wireup the bean manually"() {
        setup:
        ApplicationContext context = ApplicationContext.run([
                (APPOPTICS_CONFIG + ".apiToken")    : MOCK_APPOPTICS_API_TOKEN,
        ])
        Environment mockEnvironment = context.getEnvironment()

        when:
        AppOpticsMeterRegistryFactory factory = new AppOpticsMeterRegistryFactory(new AppOpticsConfigurationProperties(mockEnvironment))

        then:
        factory.appOpticsMeterRegistry()
    }

    void "verify AppOpticsMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (APPOPTICS_CONFIG + ".apiToken")    : MOCK_APPOPTICS_API_TOKEN,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'AppOpticsMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (APPOPTICS_CONFIG + ".apiToken")    : MOCK_APPOPTICS_API_TOKEN,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(MeterRegistryCreationListener)
        context.getBean(AppOpticsMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([AppOpticsMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify AppOpticsMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
                (APPOPTICS_CONFIG + ".apiToken")    : MOCK_APPOPTICS_API_TOKEN,
        ])

        then:
        context.findBean(AppOpticsMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        APPOPTICS_ENABLED         | true    | true
        APPOPTICS_ENABLED         | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (APPOPTICS_ENABLED)                 : true,
                (APPOPTICS_CONFIG + ".apiToken")    : MOCK_APPOPTICS_API_TOKEN,
        ])
        Optional<AppOpticsMeterRegistry> appOpticsMeterRegistry = context.findBean(AppOpticsMeterRegistry)

        then: "default properties are used"
        appOpticsMeterRegistry.isPresent()
        appOpticsMeterRegistry.get().config.enabled()
        appOpticsMeterRegistry.get().config.numThreads() == 2
        appOpticsMeterRegistry.get().config.uri() == 'https://api.appoptics.com/v1/measurements'
        appOpticsMeterRegistry.get().config.hostTag() == 'instance'
        appOpticsMeterRegistry.get().config.batchSize() == 500
        appOpticsMeterRegistry.get().config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (APPOPTICS_ENABLED)                   : true,
                (APPOPTICS_CONFIG + ".apiToken")      : MOCK_APPOPTICS_API_TOKEN,
                (APPOPTICS_CONFIG + ".numThreads")    : "77",
                (APPOPTICS_CONFIG + ".uri")           : 'http://micronaut.io',
                (APPOPTICS_CONFIG + ".batchSize")     : 250,
                (APPOPTICS_CONFIG + ".hostTag")       : 'micronaut',
                (APPOPTICS_CONFIG + ".step")          : "PT2M",
        ])
        Optional<AppOpticsMeterRegistry> appOpticsMeterRegistry = context.findBean(AppOpticsMeterRegistry)

        then:
        appOpticsMeterRegistry.isPresent()
        appOpticsMeterRegistry.get().config.enabled()
        appOpticsMeterRegistry.get().config.numThreads() == 77
        appOpticsMeterRegistry.get().config.uri() == 'http://micronaut.io'
        appOpticsMeterRegistry.get().config.batchSize() == 250
        appOpticsMeterRegistry.get().config.hostTag() == 'micronaut'
        appOpticsMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
