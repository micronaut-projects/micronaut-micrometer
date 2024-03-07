package io.micronaut.configuration.metrics.binder.executor

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.RequiredSearch
import io.micrometer.core.instrument.search.Search
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.TaskExecutors
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoopGroup
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.spockframework.runtime.IStandardStreamsListener
import org.spockframework.runtime.StandardStreamsCapturer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ExecutorService

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class ExecutorServiceMetricsBinderSpec extends Specification {

    SimpleStreamsListener captured = new SimpleStreamsListener()

    @AutoCleanup("stop")
    StandardStreamsCapturer capturer = new StandardStreamsCapturer()

    void setup() {
        capturer.addStandardStreamsListener(captured)
        capturer.start()
    }

    void "test executor service metrics"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        ExecutorService executorService = context.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.IO))

        executorService.submit({ -> } as Runnable)
        executorService.submit({ -> } as Runnable)

        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get("executor.pool.size")
        search.tags("name", "io")

        Gauge g = search.gauge()

        then: "The pool size was expanded to handle the 2 runnables"
        new PollingConditions(timeout: 3, delay: 0.1).eventually {
            g.value() > 0
        }

        cleanup:
        context.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-micrometer/issues/62")
    void "test event loop group not instrumented"() {

        when:
        ApplicationContext context = ApplicationContext.run()
        EventLoopGroup eventLoopGroup = context.findBean(EventLoopGroup, Qualifiers.byName("test"))
                .orElse(null)

        then:
        // for Micronaut 2.0 the value will not be non-null since a bean will be present
        // we are mainly asserting here that a ClassCastException doesn't occur in
        // the previous assignment. See #62
        eventLoopGroup == null || eventLoopGroup instanceof EventLoopGroup

        cleanup:
        context.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-micrometer/issues/679")
    void "test the virtual task executor is unbound with no warnings logged"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        ExecutorService executorService = context.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.BLOCKING))

        MeterRegistry registry = context.getBean(MeterRegistry)
        Search search = registry.find("executor.pool.size")
        search.tag("name", "virtual")

        then:
        executorService
        !search.gauge()
        !captured.messages.any {
            it.contains("WARN") &&
                    it.contains("Failed to bind as java.util.concurrent.ThreadPerTaskExecutor is unsupported.")
        }

        cleanup:
        context.close()
    }

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(ExecutorServiceMetricsBinder).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                             | setting
        MICRONAUT_METRICS_ENABLED                       | true
        MICRONAUT_METRICS_ENABLED                       | false
        MICRONAUT_METRICS_BINDERS + ".executor.enabled" | true
        MICRONAUT_METRICS_BINDERS + ".executor.enabled" | false
    }

    @Factory
    static class TestEventLoopGroupFactory {
        @Singleton
        @Named("test")
        @Requires(sdk = Requires.Sdk.MICRONAUT, version = "2.0.0")
        EventLoopGroup eventLoopGroup() {
            return new DefaultEventLoop()
        }
    }

    class SimpleStreamsListener implements IStandardStreamsListener {
        List<String> messages = []
        @Override void standardOut(String m) { messages << m }
        @Override void standardErr(String m) { messages << m }
    }
}
