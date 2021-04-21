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
package io.micronaut.configuration.metrics.annotation


import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.reactivex.functions.Consumer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class CountedAnnotationSpec extends Specification {

    void "test counted annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget tt = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        def result = tt.max(4, 10)
        def timer = registry.get("counted.test.max.blocking").counter()

        then:
        result == 10
        timer.count() == 1

        when:
        tt.max(4, 10)
        registry.get("counted.test.max.blocking").counter()

        then:
        timer.count() == 2

        when:
        result = tt.maxFuture(4, 10).get()
        PollingConditions conditions = new PollingConditions()


        then:
        conditions.eventually {
            def t = registry.get("counted.test.max.future").counter()
            result == 10
            t.count() == 1
        }
        timer.count() == 2

        when:
        tt.maxSingle(4, 10).subscribe( { o -> result = o} as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("counted.test.max.single").counter()

            result == 10
            rxTimer.count() == 1
        }

        when:
        tt.maxFlow(4, 10).toList().subscribe( { o -> result = o[0]} as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("counted.test.max.flowable").counter()

            result == 10
            rxTimer.count() == 1
        }


        cleanup:
        ctx.close()
    }

}
