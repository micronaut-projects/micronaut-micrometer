package io.micronaut.configuration.metrics.binder.jvm

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import javax.management.ListenerNotFoundException
import javax.management.NotificationEmitter
import java.lang.management.ManagementFactory
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class JvmMeterRegistryBinderFactorySpec extends Specification {

    void "test getting the beans manually"() {
        when:
        def binder = new JvmMeterRegistryBinderFactory()

        then:
        binder.jvmGcMetrics()
        binder.jvmMemoryMetrics()
        binder.jvmThreadMetrics()
        binder.classLoaderMetrics()
    }

    void "test getting the beans"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(JvmMeterRegistryBinderFactory)
        context.containsBean(JvmGcMetrics)
        context.containsBean(JvmMemoryMetrics)
        context.containsBean(JvmThreadMetrics)
        context.containsBean(ClassLoaderMetrics)

        cleanup:
        context.close()
    }

    void "closing the context removes the GC notification listener"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        context.getBean(MeterRegistry)
        JvmGcMetrics gcMetrics = context.getBean(JvmGcMetrics)
        def listener = gcMetrics.@gcNotificationListener
        def emitters = ManagementFactory.garbageCollectorMXBeans.findAll { it instanceof NotificationEmitter }

        expect:
        listener != null
        !emitters.isEmpty()

        when:
        context.close()
        def stillRegistered = emitters.findAll { emitter ->
            try {
                ((NotificationEmitter) emitter).removeNotificationListener(listener)
                true
            } catch (ListenerNotFoundException ignored) {
                false
            }
        }

        then:
        stillRegistered.isEmpty()
    }

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(JvmMeterRegistryBinderFactory).isPresent() == setting
        context.findBean(JvmGcMetrics).isPresent() == setting
        context.findBean(JvmMemoryMetrics).isPresent() == setting
        context.findBean(JvmThreadMetrics).isPresent() == setting
        context.findBean(ClassLoaderMetrics).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                        | setting
        MICRONAUT_METRICS_ENABLED                  | true
        MICRONAUT_METRICS_ENABLED                  | false
        MICRONAUT_METRICS_BINDERS + ".jvm.enabled" | true
        MICRONAUT_METRICS_BINDERS + ".jvm.enabled" | false
    }
}
