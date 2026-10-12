package io.micronaut.micrometer.observation.http.client.instrumentation

import io.micrometer.common.KeyValue
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import spock.lang.Specification

class DefaultClientRequestObservationConventionSpec extends Specification {

    DefaultClientRequestObservationConvention convention = new DefaultClientRequestObservationConvention()

    void "the method key value and contextual name of #method"() {
        given:
        def context = new ClientRequestObservationContext(HttpRequest.create(method, "http://localhost/books"))

        expect:
        keyValues(context).method == method.name()
        convention.getContextualName(context) == "http " + method.name().toLowerCase()

        where:
        method << HttpMethod.values()
    }

    void "the status key value of #code is #expected"() {
        given:
        def context = new ClientRequestObservationContext(HttpRequest.GET("http://localhost/books"))
        if (code != null) {
            context.setResponse(HttpResponse.status(code, "reason"))
        }

        expect:
        keyValues(context).status == expected

        where:
        code | expected
        null | "CLIENT_ERROR"
        99   | "99"
        100  | "100"
        200  | "200"
        404  | "404"
        599  | "599"
        600  | "600"
        999  | "999"
    }

    private Map<String, String> keyValues(ClientRequestObservationContext context) {
        convention.getLowCardinalityKeyValues(context).collectEntries { KeyValue kv -> [(kv.key): kv.value] }
    }
}
