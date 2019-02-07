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
package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MeterRegistryFactorySpec extends Specification {

    def "wireup the beans manually"() {
        when:
        MeterRegistryFactory factory = new MeterRegistryFactory()

        then:
        factory.compositeMeterRegistry()
        factory.meterRegistryConfigurer([], [])
    }

    void "verify beans created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)
        context.containsBean(MeterRegistryConfigurer)

        cleanup:
        context.close()
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
        context.findBean(CompositeMeterRegistry).isPresent()
        context.findBean(SimpleMeterRegistry).isPresent()
        compositeRegistry
        compositeRegistry?.registries?.size() == 1
        compositeRegistry.registries*.class.containsAll([SimpleMeterRegistry])

        cleanup:
        context.close()
    }

    @Unroll
    void "verify SimpleMeterRegistry present == #result for #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(CompositeMeterRegistry).isPresent() == result
        context.findBean(SimpleMeterRegistry).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
    }

    @Unroll
    void "verify CompositeMeterRegistry present == #result for #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(CompositeMeterRegistry).isPresent() == result
        context.findBean(SimpleMeterRegistry).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
    }
}
