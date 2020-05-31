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
package io.micronaut.configuration.metrics.micrometer.newrelic

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.step.StepRegistryConfig
import io.micrometer.newrelic.NewRelicConfig
import io.micrometer.newrelic.NewRelicMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.newrelic.NewRelicMeterRegistryFactory.NEWRELIC_CONFIG
import static io.micronaut.configuration.metrics.micrometer.newrelic.NewRelicMeterRegistryFactory.NEWRELIC_ENABLED

class NewRelicMeterRegistryFactorySpec extends Specification {

    private static String MOCK_NEWRELIC_API_KEY = "newrelicApiKey"
    private static String MOCK_NEWRELIC_ACCOUNT_ID = "newrelicApiKeyAccountId"

    void "verify NewRelicMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_CONFIG + ".apiKey")  : MOCK_NEWRELIC_API_KEY,
                (NEWRELIC_CONFIG + ".accountId")  : MOCK_NEWRELIC_ACCOUNT_ID,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'NewRelicMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_CONFIG + ".apiKey")  : MOCK_NEWRELIC_API_KEY,
                (NEWRELIC_CONFIG + ".accountId")  : MOCK_NEWRELIC_ACCOUNT_ID,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(NewRelicMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([NewRelicMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify NewRelicMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
                (NEWRELIC_CONFIG + ".apiKey")  : MOCK_NEWRELIC_API_KEY,
                (NEWRELIC_CONFIG + ".accountId")  : MOCK_NEWRELIC_ACCOUNT_ID,
        ])

        then:
        context.findBean(NewRelicMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        NEWRELIC_ENABLED          | true    | true
        NEWRELIC_ENABLED          | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_ENABLED)           : true,
                (NEWRELIC_CONFIG + ".apiKey")  : MOCK_NEWRELIC_API_KEY,
                (NEWRELIC_CONFIG + ".accountId")  : MOCK_NEWRELIC_ACCOUNT_ID,
        ])
        Optional<NewRelicMeterRegistry> newRelicMeterRegistry = context.findBean(NewRelicMeterRegistry)
        def config = context.getBean(NewRelicConfig)
        then: "default properties are used"
        newRelicMeterRegistry.isPresent()
        config.accountId() == MOCK_NEWRELIC_ACCOUNT_ID
        config.apiKey() == MOCK_NEWRELIC_API_KEY
        config.uri() == 'https://insights-collector.newrelic.com'
        config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_ENABLED)                   : true,
                (NEWRELIC_CONFIG + ".numThreads")    : "77",
                (NEWRELIC_CONFIG + ".apiKey")        : MOCK_NEWRELIC_API_KEY,
                (NEWRELIC_CONFIG + ".accountId")     : MOCK_NEWRELIC_ACCOUNT_ID,
                (NEWRELIC_CONFIG + ".uri")           : 'https://micronaut.io',
                (NEWRELIC_CONFIG + ".step")          : "PT2M",
        ])
        Optional<NewRelicMeterRegistry> newRelicMeterRegistry = context.findBean(NewRelicMeterRegistry)
        def config = context.getBean(NewRelicConfig)
        then:
        newRelicMeterRegistry.isPresent()
        config.enabled()
        config.accountId() == MOCK_NEWRELIC_ACCOUNT_ID
        config.apiKey() == MOCK_NEWRELIC_API_KEY
        config.uri() == 'https://micronaut.io'
        config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
