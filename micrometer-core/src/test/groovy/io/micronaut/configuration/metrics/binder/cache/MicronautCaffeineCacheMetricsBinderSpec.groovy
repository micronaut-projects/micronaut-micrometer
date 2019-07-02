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
package io.micronaut.configuration.metrics.binder.cache

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.RequiredSearch
import io.micronaut.cache.SyncCache
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MicronautCaffeineCacheMetricsBinderSpec extends Specification {

    def "test executor service metrics"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                'micronaut.caches.foo.maximumSize':20
        )
        SyncCache cache = context.getBean(SyncCache, Qualifiers.byName("foo"))
        cache.put("foo", "bar")

        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get("cache.size")
        search.tags("cache", "foo")

        Gauge m = search.gauge()

        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.1)

        then:"The pool size was expanded to handle the 2 runnables"
        conditions.eventually {
            m.value() > 0
        }
    }

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(MicronautCaffeineCacheMetricsBinder).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                       | setting
        MICRONAUT_METRICS_ENABLED                 | true
        MICRONAUT_METRICS_ENABLED                 | false
        MICRONAUT_METRICS_BINDERS + ".cache.enabled" | true
        MICRONAUT_METRICS_BINDERS + ".cache.enabled" | false
    }
}
