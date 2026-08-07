package io.micronaut.configuration.metrics.common.tags

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.naming.conventions.StringConvention
import spock.lang.Specification

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_COMMON_TAGS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_TAGS

class CommonTagsConfigurerSpec extends Specification {

    void "common-tags override legacy tags for duplicate keys"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                "micronaut.metrics.tags.application"       : "legacy",
                "micronaut.metrics.tags.region"            : "us-east-1",
                "micronaut.metrics.common-tags.application": "common-tags",
                "micronaut.metrics.common-tags.cluster"    : "primary"
        ])
        SimpleMeterRegistry registry = new SimpleMeterRegistry()

        when:
        context.getBean(CommonTagsConfigurer).configure(registry)
        def counter = registry.counter("test.counter")

        then:
        counter.id.getTag("application") == "common-tags"
        counter.id.getTag("region") == "us-east-1"
        counter.id.getTag("cluster") == "primary"

        cleanup:
        registry.close()
        context.close()
    }

    void "null common tag values are ignored"() {
        given:
        Environment environment = Stub() {
            containsProperties(MICRONAUT_METRICS_TAGS) >> false
            containsProperty(MICRONAUT_METRICS_TAGS) >> false
            containsProperties(MICRONAUT_METRICS_COMMON_TAGS) >> true
            getProperties(MICRONAUT_METRICS_COMMON_TAGS, StringConvention.RAW) >> [application: null, region: "us-east-1"]
        }
        SimpleMeterRegistry registry = new SimpleMeterRegistry()

        when:
        new CommonTagsConfigurer(environment).configure(registry)
        def counter = registry.counter("test.counter")

        then:
        counter.id.getTag("application") == null
        counter.id.getTag("region") == "us-east-1"

        cleanup:
        registry.close()
    }
}
