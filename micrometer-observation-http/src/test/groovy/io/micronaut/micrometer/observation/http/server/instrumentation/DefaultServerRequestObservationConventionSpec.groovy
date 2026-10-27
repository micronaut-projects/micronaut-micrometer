package io.micronaut.micrometer.observation.http.server.instrumentation

import io.micrometer.common.KeyValue
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import spock.lang.Specification

class DefaultServerRequestObservationConventionSpec extends Specification {

    DefaultServerRequestObservationConvention convention = new DefaultServerRequestObservationConvention()

    void "the method key value and contextual name of #method"() {
        given:
        def context = new ServerRequestObservationContext(HttpRequest.create(method, "/books"))

        expect:
        keyValues(context).method == method.name()
        convention.getContextualName(context) == "http " + method.name().toLowerCase()

        where:
        method << HttpMethod.values()
    }

    void "the status key value of #code is #expected"() {
        given:
        def context = new ServerRequestObservationContext(HttpRequest.GET("/books"))
        if (code != null) {
            context.setResponse(HttpResponse.status(code, "reason"))
        }

        expect:
        keyValues(context).status == expected

        where:
        code | expected
        null | "UNKNOWN"
        99   | "99"
        100  | "100"
        200  | "200"
        404  | "404"
        599  | "599"
        600  | "600"
        999  | "999"
    }

    private Map<String, String> keyValues(ServerRequestObservationContext context) {
        convention.getLowCardinalityKeyValues(context).collectEntries { KeyValue kv -> [(kv.key): kv.value] }
    }
}
