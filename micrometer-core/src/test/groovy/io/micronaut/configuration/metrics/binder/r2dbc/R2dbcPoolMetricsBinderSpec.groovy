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
package io.micronaut.configuration.metrics.binder.r2dbc

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.r2dbc.pool.PoolMetrics
import spock.lang.Specification

class R2dbcPoolMetricsBinderSpec extends Specification {

    MeterRegistry meterRegistry = Mock(MeterRegistry)

    def "test registry metrics"() {
        given:
        PoolMetrics poolMetrics = Mock(PoolMetrics)
        R2dbcPoolMetricsBinder r2dbcPoolMetricsBinder = new R2dbcPoolMetricsBinder(
                poolMetrics,
                "foo",
                []
        )

        when:
        r2dbcPoolMetricsBinder.bindTo(meterRegistry)

        then:
        1 * meterRegistry.gauge('r2dbc.pool.acquired', Tags.of("name","foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.allocated', Tags.of("name","foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.idle', Tags.of("name","foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.pending', Tags.of("name","foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.max.allocated', Tags.of("name","foo"), poolMetrics, _)
        1 * meterRegistry.gauge('r2dbc.pool.max.pending', Tags.of("name","foo"), poolMetrics, _)
        0 * _._
    }

    def "test do not registry metrics"() {
        given:
        R2dbcPoolMetricsBinder r2dbcPoolMetricsBinder = new R2dbcPoolMetricsBinder(
                null,
                "foo",
                []
        )

        when:
        r2dbcPoolMetricsBinder.bindTo(meterRegistry)

        then:
        0 * _._
    }
}