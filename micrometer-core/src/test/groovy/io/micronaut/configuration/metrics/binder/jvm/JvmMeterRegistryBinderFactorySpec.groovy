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
package io.micronaut.configuration.metrics.binder.jvm

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class JvmMeterRegistryBinderFactorySpec extends Specification {

    def "test getting the beans manually"() {
        when:
        def binder = new JvmMeterRegistryBinderFactory()

        then:
        binder.jvmGcMetrics()
        binder.jvmMemoryMetrics()
        binder.jvmThreadMetrics()
        binder.classLoaderMetrics()
    }

    def "test getting the beans"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(JvmMeterRegistryBinderFactory)
        context.containsBean(JvmGcMetrics)
        context.containsBean(JvmMemoryMetrics)
        context.containsBean(JvmThreadMetrics)
        context.containsBean(ClassLoaderMetrics)

        cleanup:
        context.close()
    }

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(JvmMeterRegistryBinderFactory).isPresent() == setting
        context.findBean(JvmGcMetrics).isPresent() == setting
        context.findBean(JvmMemoryMetrics).isPresent() == setting
        context.findBean(JvmThreadMetrics).isPresent() == setting
        context.findBean(ClassLoaderMetrics).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                       | setting
        MICRONAUT_METRICS_ENABLED                 | true
        MICRONAUT_METRICS_ENABLED                 | false
        MICRONAUT_METRICS_BINDERS + ".jvm.enabled" | true
        MICRONAUT_METRICS_BINDERS + ".jvm.enabled" | false
    }
}
