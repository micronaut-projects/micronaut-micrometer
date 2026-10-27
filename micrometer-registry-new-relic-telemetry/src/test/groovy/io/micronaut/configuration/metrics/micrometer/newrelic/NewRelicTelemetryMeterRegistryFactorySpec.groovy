package io.micronaut.configuration.metrics.micrometer.newrelic

import com.newrelic.telemetry.micrometer.NewRelicRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.configuration.metrics.micrometer.newrelictelemetry.NewRelicTelemetryMeterRegistryFactory
import io.micronaut.configuration.metrics.micrometer.newrelictelemetry.NewRelicTelemetryMicronautConfig
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.newrelictelemetry.NewRelicTelemetryMeterRegistryFactory.NEWRELIC_CONFIG
import static io.micronaut.configuration.metrics.micrometer.newrelictelemetry.NewRelicTelemetryMeterRegistryFactory.NEWRELIC_ENABLED

class NewRelicTelemetryMeterRegistryFactorySpec extends Specification {

    private static String MOCK_NEWRELIC_API_KEY = "newrelicApiKey"
    private static String MOCK_NEWRELIC_SERVICE_NAME = "newrelicApiServiceName"

    void "verify NewRelicTelemetryMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_CONFIG + ".apiKey"): MOCK_NEWRELIC_API_KEY,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'NewRelicRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_CONFIG + ".apiKey"): MOCK_NEWRELIC_API_KEY,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(NewRelicRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([NewRelicRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify NewRelicTelemetryMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg)                        : setting,
                (NEWRELIC_CONFIG + ".apiKey"): MOCK_NEWRELIC_API_KEY,
        ])

        then:
        context.findBean(NewRelicRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        NEWRELIC_ENABLED          | true    | true
        NEWRELIC_ENABLED          | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_ENABLED)           : true,
                (NEWRELIC_CONFIG + ".apiKey"): MOCK_NEWRELIC_API_KEY,
        ])
        Optional<NewRelicRegistry> newRelicMeterRegistry = context.findBean(NewRelicRegistry)
        def config = context.getBean(NewRelicTelemetryMicronautConfig)
        then: "default properties are used"
        newRelicMeterRegistry.isPresent()
        config.apiKey() == MOCK_NEWRELIC_API_KEY
        config.step() == Duration.ofMinutes(1)
        config.serviceName() == null

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (NEWRELIC_ENABLED)                : true,
                (NEWRELIC_CONFIG + ".numThreads") : "77",
                (NEWRELIC_CONFIG + ".apiKey")     : MOCK_NEWRELIC_API_KEY,
                (NEWRELIC_CONFIG + ".serviceName"): MOCK_NEWRELIC_SERVICE_NAME,
                (NEWRELIC_CONFIG + ".uri")        : 'https://micronaut.io',
                (NEWRELIC_CONFIG + ".step")       : "PT2M",
        ])
        Optional<NewRelicRegistry> newRelicMeterRegistry = context.findBean(NewRelicRegistry)
        def config = context.getBean(NewRelicTelemetryMicronautConfig)
        then:
        newRelicMeterRegistry.isPresent()
        config.enabled()
        config.serviceName() == MOCK_NEWRELIC_SERVICE_NAME
        config.apiKey() == MOCK_NEWRELIC_API_KEY
        config.uri() == 'https://micronaut.io'
        config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

    void "jvm.threads meters and all tags are exported"() {
        given:
        ApplicationContext context = ApplicationContext.run(noPublishConfig())
        // the binders are bound when the composite registry is created
        context.getBean(CompositeMeterRegistry)
        NewRelicRegistry registry = context.getBean(NewRelicRegistry)

        when:
        Counter counter = Counter.builder("test.counter").tag("plz_ignore_me", "value").register(registry)

        then:
        registry.find("jvm.threads.live").gauge() != null
        registry.find("jvm.threads.daemon").gauge() != null
        counter.id.getTag("plz_ignore_me") == "value"

        cleanup:
        context.stop()
    }

    void "the host attribute uses the local host name when it can be resolved"() {
        expect:
        NewRelicTelemetryMeterRegistryFactory.commonAttributes({ -> "my-host" }, { String name -> null }).asMap() == [host: "my-host"]
    }

    @Unroll
    void "the host attribute falls back to the environment #env when the local host name cannot be resolved"() {
        expect:
        NewRelicTelemetryMeterRegistryFactory.commonAttributes({ -> throw new UnknownHostException("no dns") }, env.&get).asMap() == expected

        where:
        env                         | expected
        [HOSTNAME: "pod-1"]         | [host: "pod-1"]
        [COMPUTERNAME: "WINDOWS-1"] | [host: "WINDOWS-1"]
        [:]                         | [:]
    }

    private static Map<String, Object> noPublishConfig() {
        [
                (NEWRELIC_CONFIG + ".apiKey"): MOCK_NEWRELIC_API_KEY,
                // never publish to New Relic from the test
                (NEWRELIC_CONFIG + ".uri")   : 'http://localhost:1',
                (NEWRELIC_CONFIG + ".step")  : '1h',
        ]
    }
}
