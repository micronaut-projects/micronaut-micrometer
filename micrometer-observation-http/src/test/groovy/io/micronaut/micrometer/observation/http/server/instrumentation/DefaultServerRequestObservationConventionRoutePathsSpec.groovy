package io.micronaut.micrometer.observation.http.server.instrumentation

import io.micronaut.http.uri.UriMatchTemplate
import spock.lang.Specification

class DefaultServerRequestObservationConventionRoutePathsSpec extends Specification {

    void "the path string of a template is computed once"() {
        given:
        DefaultServerRequestObservationConvention paths = new DefaultServerRequestObservationConvention()
        UriMatchTemplate template = UriMatchTemplate.of("/books/{id}{?max}")

        when:
        String first = paths.routePath(template)

        then:
        first == template.toPathString()
        first == "/books/{id}"
        paths.routePath(template).is(first)
        paths.routePath(UriMatchTemplate.of("/books/{id}{?max}")).is(first)
        paths.routePathCount() == 1
    }

    void "templates with the same path but different query variables have their own entry"() {
        given:
        DefaultServerRequestObservationConvention paths = new DefaultServerRequestObservationConvention()

        expect:
        paths.routePath(UriMatchTemplate.of("/books/{id}{?max}")) == "/books/{id}"
        paths.routePath(UriMatchTemplate.of("/books/{id}")) == "/books/{id}"
        paths.routePathCount() == 2
    }

    void "the cache stops growing at its maximum size"() {
        given:
        DefaultServerRequestObservationConvention paths = new DefaultServerRequestObservationConvention()

        when:
        (0..<DefaultServerRequestObservationConvention.MAX_ROUTE_PATHS + 10).each { paths.routePath(UriMatchTemplate.of("/route$it/{id}")) }

        then:
        paths.routePathCount() == DefaultServerRequestObservationConvention.MAX_ROUTE_PATHS

        and: 'templates beyond the maximum are still resolved'
        paths.routePath(UriMatchTemplate.of("/extra/{id}")) == "/extra/{id}"
        paths.routePathCount() == DefaultServerRequestObservationConvention.MAX_ROUTE_PATHS
    }
}
