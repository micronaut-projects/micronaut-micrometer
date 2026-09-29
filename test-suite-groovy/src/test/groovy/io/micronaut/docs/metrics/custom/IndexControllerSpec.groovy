package io.micronaut.docs.metrics.custom

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class IndexControllerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    MeterRegistry meterRegistry

    void "the custom counter is incremented on every request"() {
        when:
        String body = client.toBlocking().retrieve("/hello/Fred")

        then:
        body == "Hello Fred"

        when:
        def counter = meterRegistry.get("web.access")
                .tags("controller", "index", "action", "hello")
                .counter()

        then:
        counter.count() == 1.0d
    }
}
