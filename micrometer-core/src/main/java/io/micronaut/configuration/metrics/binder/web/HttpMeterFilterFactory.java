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
import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig;
import io.micronaut.configuration.metrics.binder.web.config.HttpMeterConfig;
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Arrays;

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
     * @param serverMeterConfig The HttpMeter configuration
     * @return A MeterFilter
     */
    @Bean
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".web.server")
    MeterFilter addServerPercentileMeterFilter(HttpServerMeterConfig serverMeterConfig) {
        return getMeterFilter(serverMeterConfig, WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS);
    }

    /**
     * Configure new MeterFilter for http.client.requests metrics.
     *
     * @param clientMeterConfig The HttpMeter configuration
     * @return A MeterFilter
     */
    @Bean
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".web.client")
    MeterFilter addClientPercentileMeterFilter(HttpClientMeterConfig clientMeterConfig) {
        return getMeterFilter(clientMeterConfig, WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS);
    }

    private MeterFilter getMeterFilter(HttpMeterConfig meterConfig, String metricNamePrefix) {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith(metricNamePrefix)) {
                    var builder = DistributionStatisticConfig.builder()
                        .percentiles()
                        .percentiles(Arrays.stream(meterConfig.getPercentiles()).mapToDouble(Double::doubleValue).toArray())
                        .serviceLevelObjectives(Arrays.stream(meterConfig.getSlos()).mapToDouble(d -> d * SECONDS_TO_NANOS).toArray())
                        .percentilesHistogram(meterConfig.getHistogram());

                    if (meterConfig.getMin() != null) {
                        builder.minimumExpectedValue(meterConfig.getMin() * SECONDS_TO_NANOS);
                    }

                    if (meterConfig.getMax() != null) {
                        builder.maximumExpectedValue(meterConfig.getMax() * SECONDS_TO_NANOS);
                    }

                    return builder.build().merge(config);
                }
                return config;
            }
        };
    }
}
