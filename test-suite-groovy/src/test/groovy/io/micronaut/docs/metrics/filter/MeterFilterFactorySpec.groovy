package io.micronaut.docs.metrics.filter

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "MeterFilterFactorySpec")
@MicronautTest
class MeterFilterFactorySpec extends Specification {

    @Inject
    MeterRegistry meterRegistry

    void "the common tag is added to every meter"() {
        when:
        def counter = meterRegistry.counter("filter.test")

        then:
        counter.id.getTag("scope") == "demo"
    }

    void "the method tag of the server requests meter is renamed"() {
        when:
        def timer = meterRegistry.timer("http.server.requests", "method", "GET")

        then:
        timer.id.getTag("httpmethod") == "GET"
        timer.id.getTag("method") == null
    }
}
