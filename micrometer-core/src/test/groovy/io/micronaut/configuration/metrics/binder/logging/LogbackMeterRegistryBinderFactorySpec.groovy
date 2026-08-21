package io.micronaut.configuration.metrics.binder.logging

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.cumulative.CumulativeCounter
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class LogbackMeterRegistryBinderFactorySpec extends Specification {

    void "test getting the beans manually"() {
        when:
        def binder = new LogbackMeterRegistryBinderFactory()

        then:
        binder.logbackMetrics()
    }

    void "logback metrics do not recurse when counter emits an async log event"() {
        given:
        def logger = LoggerFactory.getLogger("logback-binder-recursion-test")
        LogbackMetrics binder = new LogbackMeterRegistryBinderFactory().logbackMetrics()
        def registry = new AsyncLoggingCounterRegistry()
        binder.bindTo(registry)

        when:
        logger.info("primary event")

        then:
        !registry.awaitAsyncLog()
        registry.get("logback.events").tags("level", "info").functionCounter().count() == 1d

        cleanup:
        binder.close()
        registry.close()
    }

    void "test getting the beans"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(LogbackMeterRegistryBinderFactory)
        context.containsBean(LogbackMetrics)

        cleanup:
        context.close()
    }

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(LogbackMeterRegistryBinderFactory).isPresent() == setting
        context.findBean(LogbackMetrics).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                            | setting
        MICRONAUT_METRICS_ENABLED                      | true
        MICRONAUT_METRICS_ENABLED                      | false
        MICRONAUT_METRICS_BINDERS + ".logback.enabled" | true
        MICRONAUT_METRICS_BINDERS + ".logback.enabled" | false
    }

    void "logback metrics close removes filters and reset listener"() {
        given:
        def loggerContext = newLoggerContext()
        def binder = new MicronautLogbackMetrics([], loggerContext)
        def registry = new SimpleMeterRegistry()

        when:
        binder.bindTo(registry)

        then:
        binder.@metricsTurboFilters.size() == 1
        loggerContext.getTurboFilterList().size() == 1
        loggerContext.getCopyOfListenerList().contains(binder.@resetListener)

        when:
        binder.close()
        loggerContext.reset()

        then:
        binder.@metricsTurboFilters.isEmpty()
        loggerContext.getTurboFilterList().isEmpty()
        !loggerContext.getCopyOfListenerList().contains(binder.@resetListener)

        cleanup:
        registry.close()
        loggerContext.stop()
    }

    void "logback metrics bind same registry once"() {
        given:
        def loggerContext = newLoggerContext()
        def logger = loggerContext.getLogger("logback-binder-repeat-bind-test")
        def binder = new MicronautLogbackMetrics([], loggerContext)
        def registry = new SimpleMeterRegistry()

        when:
        binder.bindTo(registry)
        binder.bindTo(registry)
        logger.info("primary event")

        then:
        binder.@metricsTurboFilters.size() == 1
        loggerContext.getTurboFilterList().findAll { it instanceof MicronautLogbackMetrics.MetricsTurboFilter }.size() == 1
        registry.get("logback.events").tags("level", "info").functionCounter().count() == 1d

        cleanup:
        binder.close()
        registry.close()
        loggerContext.stop()
    }

    private static newLoggerContext() {
        LoggerFactory.getILoggerFactory().class.getDeclaredConstructor().newInstance()
    }

    private static final class AsyncLoggingCounterRegistry extends SimpleMeterRegistry {
        private final AtomicBoolean asyncLogged = new AtomicBoolean()
        private final CountDownLatch asyncLogLatch = new CountDownLatch(1)

        AsyncLoggingCounterRegistry() {
            super(SimpleConfig.DEFAULT, io.micrometer.core.instrument.Clock.SYSTEM)
        }

        @Override
        protected Counter newCounter(Meter.Id id) {
            return new CumulativeCounter(id) {
                @Override
                void increment(double amount) {
                    super.increment(amount)
                    if (asyncLogged.compareAndSet(false, true)) {
                        Thread.start {
                            LoggerFactory.getLogger("logback-binder-recursion-test").info("counter emitted event")
                            asyncLogLatch.countDown()
                        }
                    }
                }
            }
        }

        boolean awaitAsyncLog() {
            asyncLogLatch.await(5, TimeUnit.SECONDS)
        }
    }
}
