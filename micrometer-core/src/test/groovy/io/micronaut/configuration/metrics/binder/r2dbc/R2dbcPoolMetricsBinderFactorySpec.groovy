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

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.PoolMetrics
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.reactivestreams.Publisher
import spock.lang.Specification

class R2dbcPoolMetricsBinderFactorySpec extends Specification {

    def "test getting metrics from r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def pool = Mock(ConnectionPool)
        def metrics = new StubMetrics()
        pool.getMetrics() >> Optional.of(metrics)

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", pool)

        then:
        meterBinder.@poolMetrics == metrics
    }

    def "test getting empty metrics from r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def pool = Mock(ConnectionPool)
        pool.getMetrics() >> Optional.empty()

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", pool)

        then:
        meterBinder.@poolMetrics == null
    }

    def "test ignoring metrics when factory is not r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def factory = Mock(StubFactory)

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", factory)

        then:
        meterBinder.@poolMetrics == null
    }

    class StubFactory implements ConnectionFactory {

        @Override
        Publisher<? extends Connection> create() {
            return null
        }

        @Override
        ConnectionFactoryMetadata getMetadata() {
            return null
        }
    }

    class StubMetrics implements PoolMetrics {

        @Override
        int acquiredSize() {
            return 0
        }

        @Override
        int allocatedSize() {
            return 0
        }

        @Override
        int idleSize() {
            return 0
        }

        @Override
        int pendingAcquireSize() {
            return 0
        }

        @Override
        int getMaxAllocatedSize() {
            return 0
        }

        @Override
        int getMaxPendingAcquireSize() {
            return 0
        }
    }
}
