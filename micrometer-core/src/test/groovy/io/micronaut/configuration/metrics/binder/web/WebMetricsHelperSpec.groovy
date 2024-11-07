package io.micronaut.configuration.metrics.binder.web

import spock.lang.Specification

class WebMetricsHelperSpec extends Specification {
    def sanitizePath(String inp, String expect) {
        expect:
        expect == WebMetricsHelper.sanitizePath(inp)

        where:
        inp       | expect
        "foo"     | "foo"
        "foo/"    | "foo"
        "foo//"   | "foo"
        "f//oo//" | "f/oo"
        null      | "UNKNOWN"
        ""        | "root"
        "/"       | "root"
    }
}
