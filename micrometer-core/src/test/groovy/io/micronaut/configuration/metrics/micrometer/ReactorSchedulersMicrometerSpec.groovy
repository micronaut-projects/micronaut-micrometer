package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import java.util.concurrent.TimeUnit

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.core.util.StringUtils.FALSE

class ReactorSchedulersMicrometerSpec extends Specification {

    private static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY)         : getClass().simpleName,
            'endpoints.metrics.sensitive': false,
            (MICRONAUT_METRICS_ENABLED)  : true
    ])

    @Shared
    ApplicationContext context = embeddedServer.applicationContext

    def "enabling Reactor Schedulers metrics before Micronaut context exposes scheduler metrics in MeterRegistry"() {
        given:
        // Enable Reactor Scheduler metrics before the Micronaut context is created
        Schedulers.enableMetrics()

        when:
        SimpleMeterRegistry meterRegistry = context.getBean(SimpleMeterRegistry)
        Scheduler scheduler = Schedulers.newParallel("test", 2)

        // Schedule delayed tasks to trigger "scheduled_once" style metrics on the backing executor
        def worker = scheduler.createWorker()
        try {
            for (int i = 0; i < 5; i++) {
                worker.schedule({ /* no-op */ } as Runnable, 10, TimeUnit.MILLISECONDS)
            }
        } finally {
            worker.dispose()
        }

        // Also exercise a reactive pipeline on the instrumented scheduler
        String result = Mono.just("demo")
                .delayElement(java.time.Duration.ofMillis(5))
                .subscribeOn(scheduler)
                .map { it }
                .block()

        // Poll for meters to appear (registration can be asynchronous)
        boolean observed = false
        for (int i = 0; i < 40 && !observed; i++) {
            def names = meterRegistry.meters.collect { it.id.name } as Set
            if (names.any { (it == "executor.scheduled.once") }) {
                observed = true
                break
            }
            Thread.sleep(50)
        }

        then:
        result == "demo"
        observed

        cleanup:
        scheduler?.dispose()
        context?.close()
        Schedulers.disableMetrics()
    }



    @Factory
    static class FilteredMetricsEndpointSpecBeanFactory {

        @Bean
        @Singleton
        @Context
        @Requires(property = "spec.name", value = "ReactorSchedulersMicrometerSpec", defaultValue = FALSE)
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry()
        }
    }
}
