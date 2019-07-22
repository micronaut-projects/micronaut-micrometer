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
package io.micronaut.configuration.metrics.micrometer.logging

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.logging.LoggingMeterRegistryFactory.LOGGING_CONFIG
import static io.micronaut.configuration.metrics.micrometer.logging.LoggingMeterRegistryFactory.LOGGING_ENABLED

class LoggingMeterRegistryFactorySpec extends Specification {

    void "verify LoggingMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (LOGGING_ENABLED)           : true])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'LoggingMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (LOGGING_ENABLED)           : true])
        CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
        compositeRegistry
        context.getBean(LoggingMeterRegistry)
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([LoggingMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify LoggingMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(LoggingMeterRegistry).isPresent() == result

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | false
        LOGGING_ENABLED            | true    | true
        LOGGING_ENABLED            | false   | false
    }


    void "verify LoggingMeterRegistry bean exists changed step"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (LOGGING_ENABLED)           : true,
                (LOGGING_CONFIG + ".step")  : "PT2M",
        ])
        Optional<LoggingMeterRegistry> loggingMeterRegistry = context.findBean(LoggingMeterRegistry)

        then:
        loggingMeterRegistry.isPresent()
        loggingMeterRegistry.get().config.enabled()
        loggingMeterRegistry.get().config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }
}
