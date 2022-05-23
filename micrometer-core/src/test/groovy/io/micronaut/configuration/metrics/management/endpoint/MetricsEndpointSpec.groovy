package io.micronaut.configuration.metrics.management.endpoint

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.reflections.Reflections
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Type

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.http.HttpStatus.NOT_FOUND
import static io.micronaut.http.HttpStatus.OK
import static org.reflections.scanners.Scanners.SubTypes
import static org.reflections.scanners.Scanners.TypesAnnotated

class MetricsEndpointSpec extends Specification {

    private EmbeddedServer embeddedServer
    private ApplicationContext context
    private BlockingHttpClient client

    void cleanup() {
        client?.close()
        embeddedServer?.close()
        context?.close()
    }

    void "test the beans are available"() {
        given:
        context = ApplicationContext.builder("test").build()
        context.start()

        expect:
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)
    }

    void "test metrics endpoint disabled"() {
        when:
        run('micronaut.http.client.read-timeout': '5m',
            'endpoints.metrics.sensitive': false,
            (MICRONAUT_METRICS_ENABLED): false)

        then:
        !context.containsBean(MeterRegistry)
        !context.containsBean(CompositeMeterRegistry)

        when:
        client.retrieve(HttpRequest.GET("/metrics"), Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)
    }

    @Unroll
    void "test metrics endpoint jvmEnabled(#jvmEnabled) logbackEnabled(#logbackEnabled) uptimeEnabled(#uptimeEnabled) processorEnabled(#processorEnabled) filesEnabled(#filesEnabled)"() {
        given:
        run('endpoints.metrics.sensitive'                : false,
            (MICRONAUT_METRICS_ENABLED)                  : true,
            "micronaut.metrics.binders.jvm.enabled"      : jvmEnabled,
            "micronaut.metrics.binders.logback.enabled"  : logbackEnabled,
            "micronaut.metrics.binders.uptime.enabled"   : uptimeEnabled,
            "micronaut.metrics.binders.processor.enabled": processorEnabled,
            "micronaut.metrics.binders.files.enabled"    : filesEnabled)

        when:
        def response = client.exchange("/metrics", Map)
        Map result = response.body()

        then:
        response.code() == OK.code
        if (jvmEnabled || logbackEnabled || uptimeEnabled || processorEnabled || filesEnabled) {
            result.names.contains("jvm.buffer.count") == jvmEnabled
            result.names.contains("jvm.buffer.memory.used") == jvmEnabled
            result.names.contains("jvm.buffer.total.capacity") == jvmEnabled
            result.names.contains("jvm.classes.loaded") == jvmEnabled
            result.names.contains("jvm.classes.unloaded") == jvmEnabled
            result.names.contains("jvm.gc.live.data.size") == jvmEnabled
            result.names.contains("jvm.gc.max.data.size") == jvmEnabled
            result.names.contains("jvm.gc.memory.allocated") == jvmEnabled
            result.names.contains("jvm.gc.memory.promoted") == jvmEnabled
            result.names.contains("jvm.memory.committed") == jvmEnabled
            result.names.contains("jvm.memory.max") == jvmEnabled
            result.names.contains("jvm.memory.used") == jvmEnabled
            result.names.contains("jvm.threads.daemon") == jvmEnabled
            result.names.contains("jvm.threads.live") == jvmEnabled
            result.names.contains("jvm.threads.peak") == jvmEnabled

            result.names.contains("logback.events") == logbackEnabled

            result.names.contains("process.files.max") == filesEnabled
            result.names.contains("process.files.open") == filesEnabled

            result.names.contains("process.start.time") == uptimeEnabled
            result.names.contains("process.uptime") == uptimeEnabled

            result.names.contains("process.cpu.usage") == processorEnabled
            result.names.contains("system.cpu.count") == processorEnabled
            result.names.contains("system.cpu.usage") == processorEnabled
            result.names.contains("system.load.average.1m") == processorEnabled
        } else {
            result == [:]
        }

        where:
        jvmEnabled | logbackEnabled | uptimeEnabled | processorEnabled | filesEnabled
        true       | true           | true          | true             | true
        false      | true           | true          | true             | true
        true       | false          | true          | true             | true
        true       | true           | false         | true             | true
        true       | true           | true          | false            | true
        true       | true           | true          | true             | false
        false      | false          | false         | false            | false
    }

    @Unroll
    void "test metrics endpoint get JVM details #name success"() {
        given:
        run('endpoints.metrics.sensitive'          : false,
            (MICRONAUT_METRICS_ENABLED)            : true,
            "micronaut.metrics.binders.jvm.enabled": true)

        when:
        def response = client.exchange("/metrics/$name", Map)
        Map result = response.body() as Map

        then:
        result.name
        result.measurements
        result.description
        result.baseUnit

        where:
        name << [
                "jvm.buffer.count",
                "jvm.buffer.memory.used",
                "jvm.buffer.total.capacity",
                "jvm.classes.loaded",
                "jvm.classes.unloaded",
                "jvm.gc.live.data.size",
                "jvm.gc.max.data.size",
                "jvm.gc.memory.allocated",
                "jvm.gc.memory.promoted",
                "jvm.memory.committed",
                "jvm.memory.max",
                "jvm.memory.used",
                "jvm.threads.daemon",
                "jvm.threads.live",
                "jvm.threads.peak"
        ]
    }

    void "test metrics endpoint get JVM details success with tags"() {
        given:
        run('endpoints.metrics.sensitive'          : false,
            (MICRONAUT_METRICS_ENABLED)            : true,
            "micronaut.metrics.binders.jvm.enabled": true)

        when:
        def response = client.exchange("/metrics/jvm.buffer.count?tag=id:direct", Map)
        Map result = response.body() as Map

        then:
        result.name
        result.measurements
        result.description
        result.baseUnit

        when:
        client.exchange("/metrics/jvm.buffer.count?tag=id:blah", Map)

        then:
        HttpClientResponseException e = thrown()
        e.status == NOT_FOUND
    }

    @Unroll
    void "test metrics endpoint get JVM details #name disabled"() {
        given:
        run('endpoints.metrics.sensitive'          : false,
            (MICRONAUT_METRICS_ENABLED)            : false,
            "micronaut.metrics.binders.jvm.enabled": true)

        when:
        client.exchange("/metrics/$name", Map)

        then:
        thrown(HttpClientResponseException)

        where:
        name << [
                "jvm.buffer.count",
                "jvm.buffer.memory.used",
                "jvm.buffer.total.capacity",
                "jvm.classes.loaded",
                "jvm.classes.unloaded",
                "jvm.gc.live.data.size",
                "jvm.gc.max.data.size",
                "jvm.gc.memory.allocated",
                "jvm.gc.memory.promoted",
                "jvm.memory.committed",
                "jvm.memory.max",
                "jvm.memory.used",
                "jvm.threads.daemon",
                "jvm.threads.live",
                "jvm.threads.peak"
        ]
    }

    @Unroll
    void "test metrics endpoint get logback details #name success"() {
        given:
        run('endpoints.metrics.sensitive'              : false,
            (MICRONAUT_METRICS_ENABLED)                : true,
            "micronaut.metrics.binders.logback.enabled": true)

        when:
        def response = client.exchange("/metrics/$name", Map)
        Map result = response.body() as Map

        then:
        result.name
        result.measurements
        result.description
        result.baseUnit

        where:
        name << ["logback.events"]
    }

    @Unroll
    void "test metrics endpoint get logback details #name disabled"() {
        given:
        run('endpoints.metrics.sensitive'              : false,
            (MICRONAUT_METRICS_ENABLED)                : true,
            "micronaut.metrics.binders.logback.enabled": false)

        when:
        client.exchange("/metrics/$name", Map)

        then:
        thrown(HttpClientResponseException)

        where:
        name << ["logback.events"]
    }

    @Unroll
    void "test metrics endpoint get uptime details #name success"() {
        given:
        run('endpoints.metrics.sensitive'             : false,
            (MICRONAUT_METRICS_ENABLED)               : true,
            "micronaut.metrics.binders.uptime.enabled": true)

        when:
        def response = client.exchange("/metrics/$name", Map)
        Map result = response.body() as Map

        then:
        result.name
        result.measurements
        result.description

        where:
        name << ["process.uptime", "process.start.time"]
    }

    @Unroll
    void "test metrics endpoint get uptime details #name disabled"() {
        given:
        run('endpoints.metrics.sensitive'             : false,
            (MICRONAUT_METRICS_ENABLED)               : true,
            "micronaut.metrics.binders.uptime.enabled": false)

        when:
        client.exchange("/metrics/$name", Map)

        then:
        thrown(HttpClientResponseException)

        where:
        name << ["process.uptime", "process.start.time"]
    }

    @Unroll
    void "test metrics endpoint get processor details #name success"() {
        given:
        run('endpoints.metrics.sensitive'                : false,
            (MICRONAUT_METRICS_ENABLED)                  : true,
            "micronaut.metrics.binders.processor.enabled": true)

        when:
        def response = client.exchange("/metrics/$name", Map)
        Map result = response.body() as Map

        then:
        result.name
        result.measurements
        result.description

        where:
        name << ["system.cpu.usage", "system.cpu.count", "process.cpu.usage"]
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "test metrics endpoint get processor details unix-specific #name success"() {
        given:
        run('endpoints.metrics.sensitive'                : false,
            (MICRONAUT_METRICS_ENABLED)                  : true,
            "micronaut.metrics.binders.processor.enabled": true)

        when:
        def response = client.exchange("/metrics/$name", Map)
        Map result = response.body() as Map

        then:
        result.name
        result.measurements
        result.description

        where:
        name << ["system.load.average.1m"]
    }

    @Unroll
    void "test metrics endpoint get processor details #name disabled"() {
        given:
        run('endpoints.metrics.sensitive'                : false,
            (MICRONAUT_METRICS_ENABLED)                  : true,
            "micronaut.metrics.binders.processor.enabled": false)

        when:
        client.exchange("/metrics/$name", Map)

        then:
        thrown(HttpClientResponseException)

        cleanup:
        client.close()
        embeddedServer.close()

        where:
        name << ["system.load.average.1m", "system.cpu.usage", "system.cpu.count", "process.cpu.usage"]
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "test metrics endpoint get file details #name success"() {
        given:
        run('endpoints.metrics.sensitive'            : false,
            (MICRONAUT_METRICS_ENABLED)              : true,
            "micronaut.metrics.binders.files.enabled": true)

        when:
        def response = client.exchange("/metrics/$name", Map)
        Map result = response.body() as Map

        then:
        result.name
        result.measurements
        result.description
        result.baseUnit

        where:
        name << ["process.files.open", "process.files.max"]
    }

    @Unroll
    void "test metrics endpoint with common tags"() {
        given:
        run('endpoints.metrics.sensitive'          : false,
            (MICRONAUT_METRICS_ENABLED)            : true,
            "micronaut.metrics.binders.web.enabled": true,
            "micronaut.metrics.tags"               : ["test1": "test1-val", "test2": "test2-val"])

        expect:
        100.times {
            def response = client.exchange("/metrics/$name", Map)
            Map result = response.body() as Map
            List availableTags = result.availableTags as List
            availableTags.size() == 3
            [Tag.of("test1", "test1-val"), Tag.of("test2", "test2-val")].stream().allMatch(tag ->
                    availableTags.stream().anyMatch(item -> {
                        Map entry = item as Map
                        return entry["tag"] == tag.key && entry["values"][0] == tag.value
                    })
            )
        }

        where:
        name << ["process.files.open", "process.files.max"]
    }

    @Unroll
    void "test metrics endpoint get file details #name disabled"() {
        given:
        run('endpoints.metrics.sensitive'            : false,
            (MICRONAUT_METRICS_ENABLED)              : true,
            "micronaut.metrics.binders.files.enabled": false)

        when:
        client.exchange("/metrics/$name", Map)

        then:
        thrown(HttpClientResponseException)

        where:
        name << ["process.files.open", "process.files.max"]
    }

    @Unroll
    void "#type is annotated with @Serdeable"(Type type) {
        when:
        Reflections ref = new Reflections("io.micronaut.configuration.metrics",
                SubTypes,
                TypesAnnotated
        )
        Set<Class<?>> classes = ref.getTypesAnnotatedWith(Introspected)

        then:
        classes.contains(type)

        where:
        type << [
                MetricsEndpoint.MetricNames,
                MetricsEndpoint.MetricDetails,
                MetricsEndpoint.AvailableTag,
                MetricsEndpoint.Sample
        ]
    }

    private void run(Map<String, Object> config) {
        embeddedServer = ApplicationContext.run(EmbeddedServer, config)
        context = embeddedServer.applicationContext
        client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL).toBlocking()
    }
}
