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

import java.util.HashMap;
import java.util.Map;

/**
 * The configurations compared by the benchmarks. The {@code @Param} values of the benchmarks are the
 * {@link #name()}s of these constants; each benchmark only lists the modes that are meaningful for it.
 *
 * <p>Every mode except {@link #METRICS_PROMETHEUS} disables the Prometheus registry, so the metrics modes
 * record into the default {@code CompositeMeterRegistry} wrapping a {@code SimpleMeterRegistry}.</p>
 */
public enum MetricsMode {

    /**
     * Metrics disabled ({@code micronaut.metrics.enabled=false}) and the observation HTTP filters
     * disabled: no meter registry, no metrics filter, no {@code @Timed}/{@code @Counted} interceptor.
     * This is the baseline. The {@code @Observed} interceptor cannot be disabled, it runs against an
     * {@code ObservationRegistry} without handlers (no-op observations).
     */
    NONE,

    /**
     * Metrics disabled ({@code micronaut.metrics.enabled=false}) only: what an application gets when it
     * turns metrics off with {@code micronaut-micrometer-observation-http} on the classpath. The
     * observation HTTP filters still run, against an {@code ObservationRegistry} without handlers.
     */
    OBSERVATION_NOOP,

    /**
     * Metrics disabled plus a no-op {@code ObservationHandler} bean: the observation pipeline runs for
     * real (context, key values, handler callbacks) but nothing is recorded.
     */
    NOOP_HANDLER,

    /**
     * The classic Micronaut metrics: {@code ServerMetricsFilter} / {@code ClientMetricsFilter} (enabled by
     * {@code micrometer.observation.http.*.enabled=false}) and the {@code @Timed}/{@code @Counted}
     * interceptors, recording into a {@code SimpleMeterRegistry}.
     */
    METRICS,

    /**
     * {@link #METRICS} with {@code percentiles=0.5,0.95,0.99} and {@code histogram=true} for the HTTP
     * server and client timers.
     */
    METRICS_HISTOGRAM,

    /**
     * {@link #METRICS} recording into the Prometheus registry instead of the simple one.
     */
    METRICS_PROMETHEUS,

    /**
     * The Micrometer Observation integration with its defaults: {@code ObservationServerFilter} /
     * {@code ObservationClientFilter} and a {@code DefaultMeterObservationHandler} recording into a
     * {@code SimpleMeterRegistry}. The classic metrics filters are off (the default when
     * {@code micronaut-micrometer-observation-http} is on the classpath).
     */
    OBSERVATION,

    /**
     * {@link #OBSERVATION} with {@code micrometer.observations.long-task-timer.enabled=false}: the
     * {@code DefaultMeterObservationHandler} does not start a long task timer for every observation.
     */
    OBSERVATION_NO_LTT;

    /**
     * Property enabling {@link NoopObservationHandler}.
     */
    public static final String NOOP_HANDLER_PROPERTY = "bench.noop-handler.enabled";

    private static final String METRICS_ENABLED = "micronaut.metrics.enabled";
    private static final String OBSERVATION_SERVER = "micrometer.observation.http.server.enabled";
    private static final String OBSERVATION_CLIENT = "micrometer.observation.http.client.enabled";
    private static final String PROMETHEUS = "micronaut.metrics.export.prometheus.enabled";
    private static final String LONG_TASK_TIMER = "micrometer.observations.long-task-timer.enabled";

    /**
     * @return whether a meter registry and the metrics beans exist in this mode
     */
    public boolean metricsEnabled() {
        return this != NONE && this != OBSERVATION_NOOP && this != NOOP_HANDLER;
    }

    /**
     * @return whether the classic HTTP metrics filters are active in this mode
     */
    public boolean httpMetricsFilters() {
        return this == METRICS || this == METRICS_HISTOGRAM || this == METRICS_PROMETHEUS;
    }

    /**
     * @return whether the observation HTTP filters are active in this mode
     */
    public boolean httpObservationFilters() {
        return this == OBSERVATION_NOOP || this == NOOP_HANDLER || observationMeters();
    }

    /**
     * @return whether observations are recorded as meters in this mode
     */
    public boolean observationMeters() {
        return this == OBSERVATION || this == OBSERVATION_NO_LTT;
    }

    /**
     * @return the application properties for this mode
     */
    public Map<String, Object> properties() {
        Map<String, Object> props = new HashMap<>();
        props.put("micronaut.server.port", -1);
        props.put("micronaut.application.name", "micrometer-benchmarks");
        props.put(PROMETHEUS, false);
        switch (this) {
            case NONE -> {
                props.put(METRICS_ENABLED, false);
                props.put(OBSERVATION_SERVER, false);
                props.put(OBSERVATION_CLIENT, false);
            }
            case OBSERVATION_NOOP -> props.put(METRICS_ENABLED, false);
            case NOOP_HANDLER -> {
                props.put(METRICS_ENABLED, false);
                props.put(NOOP_HANDLER_PROPERTY, true);
            }
            case METRICS -> classicFilters(props);
            case METRICS_HISTOGRAM -> {
                classicFilters(props);
                for (String side : new String[] {"server", "client"}) {
                    props.put("micronaut.metrics.binders.web." + side + ".percentiles", "0.5,0.95,0.99");
                    props.put("micronaut.metrics.binders.web." + side + ".histogram", true);
                }
            }
            case METRICS_PROMETHEUS -> {
                classicFilters(props);
                props.put(PROMETHEUS, true);
            }
            case OBSERVATION -> { }
            case OBSERVATION_NO_LTT -> props.put(LONG_TASK_TIMER, false);
            default -> throw new IllegalStateException("Unknown mode " + this);
        }
        return props;
    }

    private static void classicFilters(Map<String, Object> props) {
        props.put(OBSERVATION_SERVER, false);
        props.put(OBSERVATION_CLIENT, false);
    }
}
