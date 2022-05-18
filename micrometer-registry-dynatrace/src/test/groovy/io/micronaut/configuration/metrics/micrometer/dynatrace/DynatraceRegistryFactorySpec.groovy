package io.micronaut.configuration.metrics.micrometer.dynatrace

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.dynatrace.DynatraceConfig
import io.micrometer.dynatrace.DynatraceMeterRegistry
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.dynatrace.DynatraceMeterRegistryFactory.DYNATRACE_CONFIG
import static io.micronaut.configuration.metrics.micrometer.dynatrace.DynatraceMeterRegistryFactory.DYNATRACE_ENABLED

class DynatraceRegistryFactorySpec extends Specification {

    private static final String DYNATRACE_MOCK_API_TOKEN = "dynatraceApiToken"
    private static final String DYNATRACE_MOCK_URI = "https://mock-api-tocken"
    private static final String DYNATRACE_MOCK_DEVICE_ID = "dynatraceDeviceId"

    void "verify DynatraceMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_CONFIG + ".apiToken"): DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")     : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId"): DYNATRACE_MOCK_DEVICE_ID,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'DynatraceMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_CONFIG + ".apiToken"): DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")     : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId"): DYNATRACE_MOCK_DEVICE_ID,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(DynatraceMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([DynatraceMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify DynatraceMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg)                           : setting,
                (DYNATRACE_CONFIG + ".apiToken"): DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")     : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId"): DYNATRACE_MOCK_DEVICE_ID,

        ])

        then:
        context.findBean(DynatraceMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        DYNATRACE_ENABLED         | true    | true
        DYNATRACE_ENABLED         | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_ENABLED)             : true,
                (DYNATRACE_CONFIG + ".apiToken"): DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".uri")     : DYNATRACE_MOCK_URI,
                (DYNATRACE_CONFIG + ".deviceId"): DYNATRACE_MOCK_DEVICE_ID,
        ])
        Optional<DynatraceMeterRegistry> dynatraceMeterRegistry = context.findBean(DynatraceMeterRegistry)
        def properties = context.getBean(ExportConfigurationProperties)
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            String get(String key) {
                return properties.export.get(key)
            }
        }

        then: "default properties are used"
        dynatraceMeterRegistry.isPresent()
        config.enabled()
        config.numThreads() == 2
        config.uri() == DYNATRACE_MOCK_URI
        config.apiToken() == DYNATRACE_MOCK_API_TOKEN
        config.deviceId() == DYNATRACE_MOCK_DEVICE_ID
        config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (DYNATRACE_ENABLED)               : true,
                (DYNATRACE_CONFIG + ".numThreads"): "77",
                (DYNATRACE_CONFIG + ".uri")       : 'https://micronaut.io',
                (DYNATRACE_CONFIG + ".step")      : "PT2M",
                (DYNATRACE_CONFIG + ".apiToken")  : DYNATRACE_MOCK_API_TOKEN,
                (DYNATRACE_CONFIG + ".deviceId")  : DYNATRACE_MOCK_DEVICE_ID,
        ])
        Optional<DynatraceMeterRegistry> dynatraceMeterRegistry = context.findBean(DynatraceMeterRegistry)
        def properties = context.getBean(ExportConfigurationProperties)
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            String get(String key) {
                return properties.export.get(key)
            }
        }

        then:
        dynatraceMeterRegistry.isPresent()
        config.enabled()
        config.numThreads() == 77
        config.apiToken() == DYNATRACE_MOCK_API_TOKEN
        config.deviceId() == DYNATRACE_MOCK_DEVICE_ID
        config.uri() == 'https://micronaut.io'
        config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }

}
