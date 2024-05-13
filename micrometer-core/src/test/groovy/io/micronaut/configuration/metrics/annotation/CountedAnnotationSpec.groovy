package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.function.Consumer

class CountedAnnotationSpec extends Specification {

    void "test counted annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget tt = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        int result = tt.max(4, 10)
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
        tt.maxFlow(4, 10).collectList().subscribe( { o -> result = o[0]} as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("counted.test.max.flowable").counter()

            result == 10
            rxTimer.count() == 1
        }

        cleanup:
        ctx.close()
    }

    void "additional tags from taggers are added"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget tt = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        int result = tt.max(4, 10)
        def timer = registry.get("counted.test.max.blocking").tags("method", "max", "parameters", "a b").counter()

        then:
        result == 10
        timer.count() == 1


        cleanup:
        ctx.close()
    }
}
