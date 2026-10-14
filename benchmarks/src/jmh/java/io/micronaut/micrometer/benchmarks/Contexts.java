/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.micrometer.benchmarks;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.micrometer.observation.http.client.ObservationClientFilter;
import io.micronaut.micrometer.observation.http.server.ObservationServerFilter;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;

import java.util.Map;

/**
 * Starts the application contexts of the benchmarks and checks that each mode wires what it claims to.
 */
public final class Contexts {

    private static final String SERVER_METRICS_FILTER = "io.micronaut.configuration.metrics.binder.web.ServerMetricsFilter";
    private static final String CLIENT_METRICS_FILTER = "io.micronaut.configuration.metrics.binder.web.ClientMetricsFilter";

    private Contexts() {
    }

    /**
     * @param mode the mode
     * @return a started context (the embedded server is not started)
     */
    public static ApplicationContext start(MetricsMode mode) {
        return start(mode, Map.of());
    }

    /**
     * @param mode  the mode
     * @param extra additional properties
     * @return a started context (the embedded server is not started)
     */
    public static ApplicationContext start(MetricsMode mode, Map<String, Object> extra) {
        Map<String, Object> props = mode.properties();
        props.putAll(extra);
        ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .banner(false)
            .properties(props)
            .start();
        expect(context, MeterRegistry.class.getName(), mode.metricsEnabled(), mode);
        return context;
    }

    /**
     * Fails unless exactly the server filters of the mode are present.
     *
     * @param context the context
     * @param mode    the mode
     */
    public static void assertServerFilters(ApplicationContext context, MetricsMode mode) {
        expect(context, SERVER_METRICS_FILTER, mode.httpMetricsFilters(), mode);
        expect(context, ObservationServerFilter.class.getName(), mode.httpObservationFilters(), mode);
    }

    /**
     * Fails unless exactly the client filters of the mode are present.
     *
     * @param context the context
     * @param mode    the mode
     */
    public static void assertClientFilters(ApplicationContext context, MetricsMode mode) {
        expect(context, CLIENT_METRICS_FILTER, mode.httpMetricsFilters(), mode);
        expect(context, ObservationClientFilter.class.getName(), mode.httpObservationFilters(), mode);
    }

    /**
     * Fails unless the meter has been registered (or not) as expected for the mode.
     *
     * @param context the context
     * @param name    the meter name
     * @param mode    the mode
     */
    public static void assertMeter(ApplicationContext context, String name, MetricsMode mode) {
        boolean expected = mode.httpMetricsFilters() || mode.observationMeters();
        boolean present = context.findBean(MeterRegistry.class)
            .map(r -> r.find(name).meter() != null)
            .orElse(false);
        if (present != expected) {
            throw new IllegalStateException("Meter " + name + " present=" + present + " for " + mode);
        }
    }

    /**
     * A request outside of the server, with the route match and route info attributes set as the server
     * sets them before the filters run.
     *
     * @param context the context (with {@link BenchController})
     * @param path    the request path
     * @return the request
     */
    @SuppressWarnings("unchecked")
    public static MutableHttpRequest<?> routedRequest(ApplicationContext context, String path) {
        MutableHttpRequest<?> request = HttpRequest.GET(path);
        UriRouteMatch<Object, Object> match = context.getBean(Router.class)
            .find(HttpMethod.GET, path, request)
            .findFirst()
            .map(m -> (UriRouteMatch<Object, Object>) m)
            .orElseThrow(() -> new IllegalStateException("No route for " + path));
        RouteAttributes.setRouteMatch(request, match);
        RouteAttributes.setRouteInfo(request, match.getRouteInfo());
        return request;
    }

    private static void expect(ApplicationContext context, String className, boolean expected, MetricsMode mode) {
        Class<?> type = ClassUtils.forName(className, Contexts.class.getClassLoader())
            .orElseThrow(() -> new IllegalStateException("Missing class " + className));
        boolean present = context.containsBean(type);
        if (present != expected) {
            throw new IllegalStateException("Unexpected presence " + present + " of " + className + " for " + mode);
        }
    }
}
