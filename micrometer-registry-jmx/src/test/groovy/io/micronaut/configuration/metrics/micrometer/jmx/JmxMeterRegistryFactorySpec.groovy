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
package io.micronaut.configuration.metrics.micrometer.jmx

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.jmx.JmxMeterRegistry
import io.micronaut.configuration.metrics.micrometer.MeterRegistryCreationListener
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.jmx.JmxMeterRegistryFactory.JMX_CONFIG
import static io.micronaut.configuration.metrics.micrometer.jmx.JmxMeterRegistryFactory.JMX_ENABLED

class JmxMeterRegistryFactorySpec extends Specification {

    void "wireup the bean manually"() {
        setup:
        ApplicationContext context = ApplicationContext.run()
        Environment mockEnvironment = context.getEnvironment()

        when:
        JmxMeterRegistryFactory factory = new JmxMeterRegistryFactory(new JmxConfigurationProperties(mockEnvironment))

        then:
        factory.jmxMeterRegistry()
    }

    void "verify JmxMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'JmxMeterRegistry'])

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
        context.getBean(JmxMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([JmxMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify JmxMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg): setting,
        ])

        then:
        context.findBean(JmxMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        JMX_ENABLED               | true    | true
        JMX_ENABLED               | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (JMX_ENABLED)                 : true,
        ])
        Optional<JmxMeterRegistry> jmxMeterRegistry = context.findBean(JmxMeterRegistry)

        then: "default properties are used"
        jmxMeterRegistry.isPresent()

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (JMX_ENABLED)                   : true,
        ])
        Optional<JmxMeterRegistry> jmxMeterRegistry = context.findBean(JmxMeterRegistry)

        then:
        jmxMeterRegistry.isPresent()

        cleanup:
        context.stop()
    }


}
