package io.micronaut.configuration.metrics.micrometer.azuremonitor

import io.micrometer.azuremonitor.AzureMonitorConfig
import io.micrometer.azuremonitor.AzureMonitorMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.step.StepMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.core.reflect.ReflectionUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Field
import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.azuremonitor.AzureMonitorMeterRegistryFactory.AZUREMONITOR_CONFIG
import static io.micronaut.configuration.metrics.micrometer.azuremonitor.AzureMonitorMeterRegistryFactory.AZUREMONITOR_ENABLED

class AzureMonitorMeterRegistryFactorySpec extends Specification {

    private static String MOCK_AZURE_INSTRUMENTATION_KEY = "micronautInstrumentationKey"

    void "verify AzureMonitorMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_CONFIG + ".instrumentationKey"): MOCK_AZURE_INSTRUMENTATION_KEY,
        ])

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'AzureMonitorMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_CONFIG + ".instrumentationKey"): MOCK_AZURE_INSTRUMENTATION_KEY,
        ])

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(AzureMonitorMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([AzureMonitorMeterRegistry])

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify AzureMonitorMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (cfg)                                        : setting,
                (AZUREMONITOR_CONFIG + ".instrumentationKey"): MOCK_AZURE_INSTRUMENTATION_KEY,
        ])

        then:
        context.findBean(AzureMonitorMeterRegistry).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        AZUREMONITOR_ENABLED      | true    | true
        AZUREMONITOR_ENABLED      | false   | false
    }

    void "verify default configuration"() {

        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_ENABLED)                       : true,
                (AZUREMONITOR_CONFIG + ".instrumentationKey"): MOCK_AZURE_INSTRUMENTATION_KEY,
        ])
        Optional<AzureMonitorMeterRegistry> azureMonitorMeterRegistry = context.findBean(AzureMonitorMeterRegistry)

        then: "default properties are used"
        azureMonitorMeterRegistry.isPresent()

        and:
        def meterRegistry = azureMonitorMeterRegistry.get()
        Field field = ReflectionUtils.getRequiredField(StepMeterRegistry, "config")
        field.setAccessible(true)
        AzureMonitorConfig config = field.get(meterRegistry)

        config.enabled()
        config.batchSize() == 10000
        config.step() == Duration.ofMinutes(1)

        cleanup:
        context.stop()
    }

    void "verify that configuration is applied"() {

        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (AZUREMONITOR_ENABLED)                       : true,
                (AZUREMONITOR_CONFIG + ".instrumentationKey"): MOCK_AZURE_INSTRUMENTATION_KEY,
                (AZUREMONITOR_CONFIG + ".numThreads")        : "77",
                (AZUREMONITOR_CONFIG + ".step")              : "PT2M",
        ])
        Optional<AzureMonitorMeterRegistry> azureMonitorMeterRegistry = context.findBean(AzureMonitorMeterRegistry)

        then:
        azureMonitorMeterRegistry.isPresent()

        and:
        def meterRegistry = azureMonitorMeterRegistry.get()
        Field field = ReflectionUtils.getRequiredField(StepMeterRegistry, "config")
        field.setAccessible(true)
        AzureMonitorConfig config = field.get(meterRegistry)
        config.instrumentationKey() == MOCK_AZURE_INSTRUMENTATION_KEY
        config.step() == Duration.ofMinutes(2)

        cleanup:
        context.stop()
    }
}
