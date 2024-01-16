package io.micronaut.configuration.metrics.micrometer.cloudwatch

import io.micrometer.cloudwatch2.CloudWatchConfig
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.context.ApplicationContext
import software.amazon.awssdk.core.client.config.SdkClientConfiguration
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.cloudwatch.CloudWatchMeterRegistryFactory.CLOUDWATCH_CONFIG
import static io.micronaut.configuration.metrics.micrometer.cloudwatch.CloudWatchMeterRegistryFactory.CLOUDWATCH_ENABLED

class CloudwatchRegistryFactorySpec extends Specification {

    void setupSpec() {
        System.setProperty("aws.region", "us-east-1")
    }

    void cleanupSpec() {
        System.clearProperty("aws.region")
    }

    void "verify CloudWatchMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 2
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'CloudWatchMeterRegistry'])

        cleanup:
        context.stop()
    }

    void "verify CloudWatchAsyncClient is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        def list = context.getBeansOfType(CloudWatchAsyncClient)
        list.size() == 1

        when:
        SdkClientConfiguration clientConfiguration = list[0].properties.clientConfiguration
        def properties = clientConfiguration.properties.attributes.attributes
        def result = properties.findAll { it.value.value.toString().startsWith("micronaut") }

        then:
        result.size() == 1

        cleanup:
        context.stop()
    }

    void "verify that cloudwatch clients can be created"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        def cloudWatchAsyncClientList = context.getBeansOfType(CloudWatchAsyncClient)
        cloudWatchAsyncClientList.size() == 1
        def cloudWatchClient = context.getBeansOfType(CloudWatchClient)
        cloudWatchClient.size() == 1

        cleanup:
        context.stop()
    }

    void "verify CompositeMeterRegistry created by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        CompositeMeterRegistry compositeRegistry = context.findBean(CompositeMeterRegistry).get()

        then:
        context.getBean(CloudWatchMeterRegistry)
        compositeRegistry
        compositeRegistry.registries.size() == 1
        compositeRegistry.registries*.class.containsAll([CloudWatchMeterRegistry])

        cleanup:
        context?.stop()
    }

    @Unroll
    void "verify CloudWatchMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(CloudWatchMeterRegistry).isPresent() == result

        cleanup:
        context?.stop()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
        CLOUDWATCH_ENABLED        | true    | true
        CLOUDWATCH_ENABLED        | false   | false
    }

    void "verify default configuration"() {
        when: "no configuration supplied"
        ApplicationContext context = ApplicationContext.run()
        Optional<CloudWatchMeterRegistry> cloudWatchMeterRegistry = context.findBean(CloudWatchMeterRegistry)

        then: "default properties are used"
        cloudWatchMeterRegistry.isPresent()
        cloudWatchMeterRegistry.get().config.enabled()
        cloudWatchMeterRegistry.get().config.prefix() == "cloudwatch"
        cloudWatchMeterRegistry.get().config.namespace() == "micronaut"
        cloudWatchMeterRegistry.get().config.batchSize() == CloudWatchConfig.MAX_BATCH_SIZE

        cleanup:
        context?.stop()
    }

    void "verify that configuration is applied"() {
        when: "non-default configuration is supplied"
        ApplicationContext context = ApplicationContext.run([
                (MICRONAUT_METRICS_ENABLED)       : true,
                (CLOUDWATCH_ENABLED)              : true,
                (CLOUDWATCH_CONFIG + ".namespace"): "someNamespace",
                (CLOUDWATCH_CONFIG + ".batchSize"): 15,
        ])
        Optional<CloudWatchMeterRegistry> cloudWatchMeterRegistry = context.findBean(CloudWatchMeterRegistry)

        then:
        cloudWatchMeterRegistry.isPresent()
        cloudWatchMeterRegistry.get().config.enabled()
        cloudWatchMeterRegistry.get().config.namespace() == "someNamespace"
        cloudWatchMeterRegistry.get().config.batchSize() == 15

        and: 'Prefix is hard coded in base library...???'
        cloudWatchMeterRegistry.get().config.prefix() == "cloudwatch"

        cleanup:
        context?.stop()
    }
}
