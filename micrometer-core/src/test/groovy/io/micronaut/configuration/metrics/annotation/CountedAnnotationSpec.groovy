package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.function.Consumer

import static java.util.concurrent.TimeUnit.MILLISECONDS

class CountedAnnotationSpec extends Specification {

    void "test counted annotation usage"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget ct = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        int result = ct.max(4, 10)
        def counter = registry.get("counted.test.max.blocking").counter()

        then:
        result == 10
        counter.count() == 1

        when:
        ct.max(4, 10)
        registry.get("counted.test.max.blocking").counter()

        then:
        counter.count() == 2

        when:
        result = ct.maxFuture(4, 10).get()
        PollingConditions conditions = new PollingConditions()

        then:
        conditions.eventually {
            def c = registry.get("counted.test.max.future").counter()
            result == 10
            c.count() == 1
        }
        counter.count() == 2

        when:
        ct.maxSingle(4, 10).subscribe( { o -> result = o} as Consumer)

        then:
        conditions.eventually {
            def rxCounter = registry.get("counted.test.max.single").counter()

            result == 10
            rxCounter.count() == 1
        }

        when:
        ct.maxFlow(4, 10).collectList().subscribe( { o -> result = o[0]} as Consumer)

        then:
        conditions.eventually {
            def rxCounter = registry.get("counted.test.max.flowable").counter()

            result == 10
            rxCounter.count() == 1
        }

        cleanup:
        ctx.close()
    }

    void "additional tags from taggers are added"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget ct = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        int result = ct.max(4, 10)
        def counter = registry.get("counted.test.max.blocking").tags("method", "max", "parameters", "a b").counter()

        then:
        result == 10
        counter.count() == 1


        cleanup:
        ctx.close()
    }

    void "extraTags takes priority if same tag key"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget ct = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = ct.maxWithExtraTags(4, 10)
        def counter = registry.get("counted.test.maxWithExtraTags.blocking").tags("method", "CountedTarget.maxWithExtraTags", "parameters", "a b").counter()

        then:
        result == 10
        counter.count() == 1

        cleanup:
        ctx.close()
    }

    void "taggers are filtered if filter present"(){
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget ct = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = ct.maxWithOptions(4, 10)
        registry.get("counted.test.maxWithOptions.blocking").tags("method", "maxWithOptions", "parameters", "a b").counter()

        then:
        thrown(MeterNotFoundException)

        when:
        def counter = registry.get("counted.test.maxWithOptions.blocking").tags("method", "maxWithOptions").counter()

        then:
        result == 10
        counter.count() == 1

        cleanup:
        ctx.close()
    }

    void "filters are applied in order"(){
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CountedTarget cc = ctx.getBean(CountedTarget)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        when:
        Integer result = cc.max(4, 10)
        registry.get("counted.test.max.blocking").tags("ordered", "1", "parameters", "a b").timer()

        then:
        thrown(MeterNotFoundException)

        when:
        def counter = registry.get("counted.test.max.blocking").tags("ordered", "2", "parameters", "a b").counter()

        then:
        result == 10
        counter.count() == 1

        cleanup:
        ctx.close()
    }
}
