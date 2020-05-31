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
import io.micronaut.context.env.PropertySource
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.server.netty.InstrumentedNioEventLoopGroupFactory
import io.micronaut.http.server.netty.NettyHttpServer
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.*
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.search.RequiredSearch

class MicronautNettyInstrumentedQueuesSpec extends Specification {

     void "test netty server use instrumented queues"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
              [MICRONAUT_METRICS_ENABLED: true,
               (MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled"): true]
        ))
        beanContext.start()

        when:
        NettyHttpServer server = beanContext.getBean(NettyHttpServer)
        server.start()

        then:
        server.eventLoopGroupFactory
        server.eventLoopGroupFactory.class == InstrumentedNioEventLoopGroupFactory

        cleanup:
        beanContext.close()
    }

    def "test netty server instrumented queues metrics"() {
        when:
        ApplicationContext context = ApplicationContext.run(
              [MICRONAUT_METRICS_ENABLED: true,
               (MICRONAUT_METRICS_BINDERS + ".netty.queues.enabled"): true]
        )
        context.start()
        NettyHttpServer server = context.getBean(NettyHttpServer)
        server.start()
        DummyClient client = context.getBean(DummyClient)
        client.root()
        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get(dot(NETTY, QUEUE, WAIT_TIME))
        Collection<Timer> waitTimers = search.timers()
        search = registry.get(dot(NETTY, QUEUE, EXECUTION_TIME))
        Collection<Timer> executionTimers = search.timers()

        then:
        !waitTimers.empty
        !executionTimers.empty
        for (Timer t: waitTimers) {
            t.count() == 0
        }
        for (Timer t: executionTimers) {
            t.count() == 0
        }

        when:
        client.root()
        client.root()
        client.root()
        client.root()
        client.root()
        def waitTime = 0
        def executionTime = 0
        for (Timer t: waitTimers) {
            waitTime += t.count()
        }
        for (Timer t: executionTimers) {
            executionTime += t.count()
        }

        then:
        waitTime > 0
        executionTime > 0

        cleanup:
        context.close()
    }

    @Client('/foo')
    private static interface DummyClient {
        @Get
        String root()
    }

    @Controller('/foo')
    private static class DummyController {
        @Get
        String root() {
            return "root"
        }
    }

}
