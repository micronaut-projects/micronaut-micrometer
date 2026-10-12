package io.micronaut.micrometer.observation.http.server.instrumentation

import io.micronaut.http.uri.UriMatchTemplate
import spock.lang.Specification

class RoutePathsSpec extends Specification {

    void "the path string of a template is computed once"() {
        given:
        RoutePaths paths = new RoutePaths()
        UriMatchTemplate template = UriMatchTemplate.of("/books/{id}{?max}")

        when:
        String first = paths.pathString(template)

        then:
        first == template.toPathString()
        first == "/books/{id}"
        paths.pathString(template).is(first)
        paths.pathString(UriMatchTemplate.of("/books/{id}{?max}")).is(first)
        paths.size() == 1
    }

    void "templates with the same path but different query variables have their own entry"() {
        given:
        RoutePaths paths = new RoutePaths()

        expect:
        paths.pathString(UriMatchTemplate.of("/books/{id}{?max}")) == "/books/{id}"
        paths.pathString(UriMatchTemplate.of("/books/{id}")) == "/books/{id}"
        paths.size() == 2
    }

    void "the cache stops growing at its maximum size"() {
        given:
        RoutePaths paths = new RoutePaths()

        when:
        (0..<RoutePaths.MAX_ENTRIES + 10).each { paths.pathString(UriMatchTemplate.of("/route$it/{id}")) }

        then:
        paths.size() == RoutePaths.MAX_ENTRIES

        and: 'templates beyond the maximum are still resolved'
        paths.pathString(UriMatchTemplate.of("/extra/{id}")) == "/extra/{id}"
        paths.size() == RoutePaths.MAX_ENTRIES
    }
}
