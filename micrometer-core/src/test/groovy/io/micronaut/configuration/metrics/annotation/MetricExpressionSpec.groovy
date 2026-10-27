package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MetricExpressionSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'spec.name'                 : 'MetricExpressionSpec',
            'metric.expr.description'   : 'description',
            'metric.expr.tag'           : 'from-env'
    ])

    @Shared
    MetricExpressionTarget target = context.getBean(MetricExpressionTarget)

    @Shared
    MeterRegistry registry = context.getBean(MeterRegistry)

    void "@Timed evaluates expressions in the name and the description"() {
        when:
        target.timedScalar(1, 2)
        Timer timer = registry.get('timed.expr.name').timer()

        then:
        timer.count() == 1
        timer.id.description == 'timed description'
    }

    void "@Timed evaluates expressions in extraTags"() {
        when:
        target.timedExtraTags(1, 2)
        Timer timer = registry.get('timed.expr.tags').timer()

        then:
        timer.count() == 1
        timer.id.getTag('literal') == 'value'
        timer.id.getTag('env') == 'from-env'
    }

    void "@Timed evaluates expressions that reference method arguments in extraTags"() {
        when:
        target.timedArgument('first')
        target.timedArgument('second')

        then:
        registry.get('timed.expr.args').tag('arg', 'first').timer().count() == 1
        registry.get('timed.expr.args').tag('arg', 'second').timer().count() == 1
    }

    void "repeated @Timed evaluates expressions in each annotation"() {
        when:
        target.timedRepeated(1, 2)

        then:
        registry.get('timed.expr.repeated1').timer().count() == 1
        registry.get('timed.expr.repeated2').tag('env', 'from-env').timer().count() == 1
    }

    void "@Counted evaluates expressions in the name and the description"() {
        when:
        target.countedScalar(1, 2)
        Counter counter = registry.get('counted.expr.name').counter()

        then:
        counter.count() == 1
        counter.id.description == 'counted description'
    }

    void "@Counted evaluates expressions in extraTags"() {
        when:
        target.countedExtraTags(1, 2)
        Counter counter = registry.get('counted.expr.tags').counter()

        then:
        counter.count() == 1
        counter.id.getTag('literal') == 'value'
        counter.id.getTag('env') == 'from-env'
    }
}
