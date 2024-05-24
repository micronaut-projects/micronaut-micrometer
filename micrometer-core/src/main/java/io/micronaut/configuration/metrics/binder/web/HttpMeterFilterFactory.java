/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Objects;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Optional filter for adding percentile to HTTP metrics.
 *
 *  @author umutkocasarac
 */
@Factory
@RequiresMetrics
@Requires(property = WebMetricsPublisher.ENABLED, notEquals = FALSE)
public class HttpMeterFilterFactory {

    public static final double SECONDS_TO_NANOS = 1_000_000_000d;

    /**
     * Configure new MeterFilter for http.server.requests metrics.
     *
     * @param percentiles The percentiles
     * @param histogram If a histogram should be published
     * @param min       the minimum time (in s) value expected.
     * @param max       the maximum time (in s) value expected.
     * @param slos      the user-defined service levels objectives (in s) to create.
     * @return A MeterFilter
     */
    @Bean
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".web.server")
    MeterFilter addServerPercentileMeterFilter(
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.server.percentiles:}") Double[] percentiles,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.server.histogram:false}") Boolean histogram,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.server.min:-1}") Double min,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.server.max:-1}") Double max,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.server.slos:}") Double[] slos
    ) {
        return getMeterFilter(percentiles, histogram, min, max, slos, WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS);
    }

    /**
     * Configure new MeterFilter for http.client.requests metrics.
     *
     * @param percentiles The percentiles
     * @param histogram If a histogram should be published
     * @param min       the minimum time (in s) value expected.
     * @param max       the maximum time (in s) value expected.
     * @param slos      the user-defined service levels objectives (in s) to create.
     * @return A MeterFilter
     */
    @Bean
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".web.client")
    MeterFilter addClientPercentileMeterFilter(
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.client.percentiles:}") Double[] percentiles,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.client.histogram:false}") Boolean histogram,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.client.min:-1}") Double min,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.client.max:-1}") Double max,
        @Value("${" + MICRONAUT_METRICS_BINDERS + ".web.client.slos:}") Double[] slos
    ) {
        return getMeterFilter(percentiles, histogram, min, max, slos, WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS);
    }

    private MeterFilter getMeterFilter(Double[] percentiles, Boolean histogram, Double minMs, Double maxMs, Double[] slos, String metricNamePrefix) {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith(metricNamePrefix)) {
                    var builder = DistributionStatisticConfig.builder()
                        .percentiles()
                        .percentiles(Arrays.stream(percentiles).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray())
                        .serviceLevelObjectives(Arrays.stream(slos).filter(Objects::nonNull).mapToDouble(d -> d * SECONDS_TO_NANOS).toArray())
                        .percentilesHistogram(histogram);

                    if (minMs != -1) {
                        builder.minimumExpectedValue(minMs * SECONDS_TO_NANOS);
                    }

                    if (maxMs != -1) {
                        builder.maximumExpectedValue(maxMs * SECONDS_TO_NANOS);
                    }

                    return builder.build().merge(config);
                }
                return config;
            }
        };
    }
}
