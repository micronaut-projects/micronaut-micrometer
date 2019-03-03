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
package io.micronaut.configuration.metrics.micrometer.cloudwatch

import io.micrometer.cloudwatch.CloudWatchConfig
import io.micrometer.cloudwatch.CloudWatchMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.configuration.metrics.micrometer.MeterRegistryCreationListener
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.cloudwatch.CloudWatchMeterRegistryFactory.CLOUDWATCH_CONFIG
import static io.micronaut.configuration.metrics.micrometer.cloudwatch.CloudWatchMeterRegistryFactory.CLOUDWATCH_ENABLED

class CloudwatchRegistryFactorySpec extends Specification {

    void "wireup the bean manually"() {
        setup:
        Environment mockEnvironment = Stub()
        mockEnvironment.getProperty(_, _) >> Optional.empty()

        when:
        CloudWatchMeterRegistryFactory factory = new CloudWatchMeterRegistryFactory(new CloudWatchConfigurationProperties(mockEnvironment))

        then:
        factory.cloudWatchMeterRegistry()
    }

    void "verify CloudWatchMeterRegistry is created by default when this configuration used"() {
        when:
            ApplicationContext context = ApplicationContext.run()

        then:
            context.getBeansOfType(MeterRegistry).size() == 2
            context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'CloudWatchMeterRegistry'])

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
        context.getBean(CloudWatchMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([CloudWatchMeterRegistry])

        cleanup:
        context?.stop()
    }

    @Unroll
    void "verify CloudWatchMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(CloudWatchMeterRegistry).isPresent() == result

        cleanup:
        context?.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        CLOUDWATCH_ENABLED        | true    | true
        CLOUDWATCH_ENABLED        | false   | false
    }

    void "verify default configuration"() {
        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run()
        Optional<CloudWatchMeterRegistry> cloudWatchMeterRegistry = context.findBean(CloudWatchMeterRegistry)

        then: "default properties are used"
        cloudWatchMeterRegistry.isPresent()
        cloudWatchMeterRegistry.get().config.enabled()
        cloudWatchMeterRegistry.get().config.prefix() == "cloudwatch"
        cloudWatchMeterRegistry.get().config.namespace() == "micronaut"
        cloudWatchMeterRegistry.get().config.batchSize() == CloudWatchConfig.MAX_BATCH_SIZE

        cleanup:
        context?.stop()
    }

    void "verify that configuration is applied"() {
        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (MICRONAUT_METRICS_ENABLED)          : true,
                (CLOUDWATCH_ENABLED)                 : true,
                (CLOUDWATCH_CONFIG + ".namespace")   : "someNamespace",
                (CLOUDWATCH_CONFIG + ".batchSize")   : 15,
        ])
        Optional<CloudWatchMeterRegistry> cloudWatchMeterRegistry = context.findBean(CloudWatchMeterRegistry)

        then:
        cloudWatchMeterRegistry.isPresent()
        cloudWatchMeterRegistry.get().config.enabled()
        cloudWatchMeterRegistry.get().config.namespace() == "someNamespace"
        cloudWatchMeterRegistry.get().config.batchSize() == 15

        and: 'Prefix is hard coded in base library...???'
        cloudWatchMeterRegistry.get().config.prefix() == "cloudwatch"


        cleanup:
        context?.stop()
    }
}
