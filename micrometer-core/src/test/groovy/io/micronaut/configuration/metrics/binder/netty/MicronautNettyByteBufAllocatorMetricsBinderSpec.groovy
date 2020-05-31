/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.configuration.metrics.binder.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.*
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.search.RequiredSearch
import io.micronaut.configuration.metrics.binder.netty.ByteBufAllocatorMetricsBinder.ByteBufAllocatorMetricKind

class MicronautNettyByteBufAllocatorMetricsBinderSpec extends Specification {

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(ByteBufAllocatorMetricsBinder).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                                                             | setting | result
        MICRONAUT_METRICS_ENABLED                                       | true    | false
        MICRONAUT_METRICS_ENABLED                                       | false   | false
        MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled" | true    | true
        MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled" | false   | false
    }


    def "test ByteBufAllocator custom metrics"() {
        when:
        ApplicationContext context = ApplicationContext.run(
              [MICRONAUT_METRICS_ENABLED: true,
               (MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled"): true,
               (MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.metrics"): [ByteBufAllocatorMetricKind.POOLED_ALLOCATOR, ByteBufAllocatorMetricKind.UNPOOLED_ALLOCATOR]]
        )
        Optional<ByteBufAllocatorMetricsBinder> optBinder = context.findBean(ByteBufAllocatorMetricsBinder)

        then:
        optBinder.isPresent()
        optBinder.get().kinds.size() == 2
        optBinder.get().kinds.contains(ByteBufAllocatorMetricKind.POOLED_ALLOCATOR)
        optBinder.get().kinds.contains(ByteBufAllocatorMetricKind.UNPOOLED_ALLOCATOR)

        cleanup:
        context.close()
    }

    def "test ByteBufAllocator metrics binder is present"() {
        when:
        ApplicationContext context = ApplicationContext.run(
              [MICRONAUT_METRICS_ENABLED: true,
               (MICRONAUT_METRICS_BINDERS + ".netty.bytebuf-allocators.enabled"): true]
        )

        then:
        context.containsBean(ByteBufAllocatorMetricsBinder)

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)
        Tags pooled = Tags.of(ALLOC, POOLED)
        RequiredSearch search = registry.get(dot(NETTY, ALLOC, MEMORY, USED))
        search.tags(pooled.and(MEMORY, DIRECT))
        Gauge gauge = search.gauge()

        then:
        gauge
        gauge.value() >= 0

        when:
        def initialValue = gauge.value()
        def server = context.getBean(EmbeddedServer)
        server.start()
        DummyClient client = context.getBean(DummyClient)
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.1)

        then:
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        client.root() == 'root'
        conditions.eventually {
            gauge.value() >= initialValue
        }

        cleanup:
        context.close()
    }

    @Client('/dummy')
    private static interface DummyClient {
        @Get
        String root()
    }

    @Controller('/dummy')
    private static class DummyController {
        @Get
        String root() {
            return "root"
        }
    }
}
