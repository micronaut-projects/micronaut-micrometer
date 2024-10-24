package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.function.Consumer

import static java.util.concurrent.TimeUnit.MILLISECONDS

class TimeAnnotationSpec extends Specification {

    void "test timed annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = tt.max(4, 10)
        def timer = registry.get("timed.test.max.blocking").timer()

        then:
        result == 10
        timer.count() == 1
        timer.totalTime(MILLISECONDS) > 0

        when:
        result = tt.maxFuture(4, 10).get()
        PollingConditions conditions = new PollingConditions()

        then:
        conditions.eventually {
            def t = registry.get("timed.test.max.future").timer()
            result == 10
            t.count() == 1
            t.totalTime(MILLISECONDS) > 0

        }
        timer.count() == 1

        when:
        tt.maxSingle(4, 10).subscribe({ o -> result = o } as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("timed.test.max.single").timer()

            result == 10
            rxTimer.count() == 1
            rxTimer.totalTime(MILLISECONDS) > 0
        }

        when:
        tt.maxFlow(4, 10).collectList().subscribe({ o -> result = o[0] } as Consumer)

        then:
        conditions.eventually {
            def rxTimer = registry.get("timed.test.max.flowable").timer()

            result == 10
            rxTimer.count() == 1
            rxTimer.totalTime(MILLISECONDS) > 0
        }

        when: "repeated annotation is used"
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

    void "additional tags from taggers are added"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = tt.max(4, 10)
        def timer = registry.get("timed.test.max.blocking").tags("method", "max", "parameters", "a b").timer()

        then:
        result == 10
        timer.count() == 1
        timer.totalTime(MILLISECONDS) > 0

        cleanup:
        ctx.close()
    }

    void "extraTags takes priority if same tag key"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = tt.maxWithExtraTags(4, 10)
        def timer = registry.get("timed.test.maxWithExtraTags.blocking").tags("method", "TimedTarget.maxWithExtraTags", "parameters", "a b").timer()

        then:
        result == 10
        timer.count() == 1
        timer.totalTime(MILLISECONDS) > 0

        cleanup:
        ctx.close()
    }

    void "taggers are filtered if filter present"(){
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = tt.maxWithOptions(4, 10)
        registry.get("timed.test.maxWithOptions.blocking").tags("method", "maxWithOptions", "parameters", "a b").timer()

        then:
        thrown(MeterNotFoundException)

        when:
        def timer = registry.get("timed.test.maxWithOptions.blocking").tags("method", "maxWithOptions").timer()

        then:
        result == 10
        timer.count() == 1
        timer.totalTime(MILLISECONDS) > 0

        cleanup:
        ctx.close()
    }

    void "taggers are applied in order"(){
        given:
        ApplicationContext ctx = ApplicationContext.run()
        TimedTarget tt = ctx.getBean(TimedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = tt.max(4, 10)
        registry.get("timed.test.max.blocking").tags("ordered", "1", "parameters", "a b").timer()

        then:
        thrown(MeterNotFoundException)

        when:
        def timer = registry.get("timed.test.max.blocking").tags("ordered", "2", "parameters", "a b").timer()

        then:
        result == 10
        timer.count() == 1
        timer.totalTime(MILLISECONDS) > 0

        cleanup:
        ctx.close()
    }
}
