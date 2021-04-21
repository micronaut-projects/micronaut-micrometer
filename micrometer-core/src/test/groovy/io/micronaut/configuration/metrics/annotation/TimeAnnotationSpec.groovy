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

import java.util.concurrent.TimeUnit

class TimeAnnotationSpec extends Specification {

    void "test timed annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        def result = tt.max(4, 10)
        def timer = registry.get("timed.test.max.blocking").timer()

        then:
        result == 10
        timer.count() == 1
        timer.totalTime(TimeUnit.MILLISECONDS) > 0

        when:
        result = tt.maxFuture(4, 10).get()
        PollingConditions conditions = new PollingConditions()


        then:
        conditions.eventually {
            def t = registry.get("timed.test.max.future").timer()
            result == 10
            t.count() == 1
            t.totalTime(TimeUnit.MILLISECONDS) > 0

        }
        timer.count() == 1

        when:
        tt.maxSingle(4, 10).subscribe( { o -> result = o} as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("timed.test.max.single").timer()

            result == 10
            rxTimer.count() == 1
            rxTimer.totalTime(TimeUnit.MILLISECONDS) > 0
        }

        when:
        tt.maxFlow(4, 10).toList().subscribe( { o -> result = o[0]} as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("timed.test.max.flowable").timer()

            result == 10
            rxTimer.count() == 1
            rxTimer.totalTime(TimeUnit.MILLISECONDS) > 0
        }

        when:"repeated annotation is used"
        tt.repeated(1, 2)

        then:
        conditions.eventually {
            def repeatedTimer1 = registry.get("timed.test.repeated1").timer()
            def repeatedTimer2 = registry.get("timed.test.repeated2").timer()

            repeatedTimer1.count() == 1
            repeatedTimer2.count() == 1
        }

        cleanup:
        ctx.close()
    }


}
