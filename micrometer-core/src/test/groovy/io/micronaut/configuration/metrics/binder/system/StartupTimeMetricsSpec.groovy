package io.micronaut.configuration.metrics.binder.system

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.TimeGauge
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class StartupTimeMetricsSpec extends Specification {

    void "started and ready time are registered when the server starts"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        MeterRegistry registry = server.applicationContext.getBean(MeterRegistry)

        when:
        TimeGauge started = registry.find('application.started.time').timeGauge()
        TimeGauge ready = registry.find('application.ready.time').timeGauge()

        then:
        started
        ready
        started.value(TimeUnit.MILLISECONDS) > 0
        ready.value(TimeUnit.MILLISECONDS) >= started.value(TimeUnit.MILLISECONDS)
        started.id.description == 'Time taken to start the application'
        started.id.getTag('main.application.class') == ready.id.getTag('main.application.class')

        cleanup:
        server.close()
    }

    void "ready time is not registered before an embedded application starts"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        expect: 'the netty server is on the classpath but not started'
        registry.find('application.started.time').timeGauge()
        !registry.find('application.ready.time').timeGauge()

        cleanup:
        ctx.close()
    }

    void "gauges are registered whether the registry is bound before or after the events"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder().start()
        StartupTimeMetrics metrics = new StartupTimeMetrics(ctx)
        MeterRegistry early = new SimpleMeterRegistry()
        MeterRegistry late = new SimpleMeterRegistry()

        when:
        metrics.bindTo(early)
        metrics.onApplicationEvent(new StartupEvent(ctx))

        then:
        early.find('application.started.time').timeGauge()
        !early.find('application.ready.time').timeGauge()

        when:
        metrics.ready(StartupTimeMetrics.uptimeMillis())
        metrics.bindTo(late)

        then:
        early.find('application.ready.time').timeGauge()
        late.find('application.started.time').timeGauge()
        late.find('application.ready.time').timeGauge()

        cleanup:
        ctx.close()
    }

    void "startup time metrics can be disabled: #config"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config)

        expect:
        !server.applicationContext.containsBean(StartupTimeMetrics)
        !server.applicationContext.containsBean(ApplicationReadyTimeMetrics)
        server.applicationContext.findBean(MeterRegistry).map { it.find('application.started.time').timeGauge() == null }.orElse(true)

        cleanup:
        server.close()

        where:
        config << [['micronaut.metrics.binders.startup.enabled': false], ['micronaut.metrics.enabled': false]]
    }
}
