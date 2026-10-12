package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class StaticTagsSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()

    StaticTagsTarget target = ctx.getBean(StaticTagsTarget)
    MeterRegistry registry = ctx.getBean(MeterRegistry)

    void "a successful @Timed invocation has the extra tags and exception=none, the exception tag winning over an extra tag"() {
        when:
        target.timed(false)
        target.timed(false)

        then:
        tags(registry.get("static.tags.timed").tag("exception", "none").timer()) == [exception: "none", k1: "v1"]
        registry.get("static.tags.timed").tag("exception", "none").timer().count() == 2
    }

    void "a failed @Timed invocation has the exception tag"() {
        when:
        target.timed(true)

        then:
        thrown(IllegalStateException)
        tags(registry.get("static.tags.timed").tag("exception", "IllegalStateException").timer()) == [exception: "IllegalStateException", k1: "v1"]
    }

    void "a successful @Counted invocation has the extra tags, exception=none and result=success"() {
        when:
        target.counted(false)

        then:
        tags(registry.get("static.tags.counted").tag("result", "success").counter()) == [exception: "none", k1: "v1", result: "success"]
    }

    void "a failed @Counted invocation has the exception and result=failure tags"() {
        when:
        target.counted(true)

        then:
        thrown(IllegalStateException)
        tags(registry.get("static.tags.counted").tag("result", "failure").counter()) == [exception: "IllegalStateException", k1: "v1", result: "failure"]
    }

    void "invalid extra tags do not record a timer and do not fail the invocation"() {
        when:
        int result = target.invalidExtraTags()

        then:
        result == 1
        registry.find("static.tags.invalid").timer() == null
    }

    private static Map<String, String> tags(Meter meter) {
        // without the common tags of the test configuration
        meter.id.tags.findAll { it.key != "scope" }.collectEntries { [(it.key): it.value] }
    }
}
