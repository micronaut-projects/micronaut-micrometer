package io.micronaut.configuration.metrics.binder.jvm

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmCompilationMetrics
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadDeadlockMetrics
import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import spock.lang.Specification

class ExtendedJvmMeterRegistryBinderFactorySpec extends Specification {

    void "info, compilation, heap pressure and virtual thread metrics are enabled by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        expect:
        ctx.containsBean(JvmInfoMetrics)
        ctx.containsBean(JvmCompilationMetrics)
        ctx.containsBean(JvmHeapPressureMetrics)
        ctx.containsBean(VirtualThreadMetrics)
        !ctx.containsBean(JvmThreadDeadlockMetrics)

        registry.find('jvm.info').gauge()
        registry.find('jvm.compilation.time').functionCounter()
        registry.find('jvm.gc.overhead').gauge()
        registry.find('jvm.threads.virtual.pinned').timer()
        !registry.find('jvm.threads.deadlocked').gauge()

        cleanup:
        ctx.close()
    }

    void "the deadlock metrics can be enabled"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.metrics.binders.jvm.deadlock.enabled': true])
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        expect:
        ctx.containsBean(JvmThreadDeadlockMetrics)
        registry.find('jvm.threads.deadlocked').gauge().value() == 0d
        registry.find('jvm.threads.deadlocked.monitor').gauge()

        cleanup:
        ctx.close()
    }

    void "binders can be disabled individually"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.metrics.binders.jvm.info.enabled'         : false,
                'micronaut.metrics.binders.jvm.compilation.enabled'  : false,
                'micronaut.metrics.binders.jvm.heap-pressure.enabled': false,
                'micronaut.metrics.binders.virtual-threads.enabled'  : false
        ])

        expect:
        !ctx.containsBean(JvmInfoMetrics)
        !ctx.containsBean(JvmCompilationMetrics)
        !ctx.containsBean(JvmHeapPressureMetrics)
        !ctx.containsBean(VirtualThreadMetrics)

        cleanup:
        ctx.close()
    }

    void "binders are disabled with the jvm metrics or all metrics: #config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(config + ['micronaut.metrics.binders.jvm.deadlock.enabled': true])

        expect:
        !ctx.containsBean(JvmInfoMetrics)
        !ctx.containsBean(JvmCompilationMetrics)
        !ctx.containsBean(JvmHeapPressureMetrics)
        !ctx.containsBean(JvmThreadDeadlockMetrics)

        cleanup:
        ctx.close()

        where:
        config << [['micronaut.metrics.binders.jvm.enabled': false], ['micronaut.metrics.enabled': false]]
    }

    void "closeable binders are closed with the context"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBeanDefinition(JvmHeapPressureMetrics).stringValue(Bean, 'preDestroy').get() == 'close'
        ctx.getBeanDefinition(VirtualThreadMetrics).stringValue(Bean, 'preDestroy').get() == 'close'

        when:
        ctx.getBean(JvmHeapPressureMetrics)
        ctx.getBean(VirtualThreadMetrics)
        ctx.close()

        then:
        noExceptionThrown()
    }
}
