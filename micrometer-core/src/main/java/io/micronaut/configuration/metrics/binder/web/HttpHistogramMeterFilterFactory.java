/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Optional filter for adding histograms to HTTP metrics.
 *
 * @author lcavadas
 */
@Factory
@RequiresMetrics
@Requires(property = WebMetricsPublisher.ENABLED, notEquals = FALSE)
@Primary
public class HttpHistogramMeterFilterFactory {

  /**
   * Configure new MeterFilter for http.server.requests metrics.
   *
   * @param histogram If a histogram should be published
   * @return A MeterFilter
   */
  @Bean
  @Singleton
  @Requires(property = MICRONAUT_METRICS_BINDERS + ".web.server.histogram")
  MeterFilter addServerPercentileMeterFilter(@Value("${" + MICRONAUT_METRICS_BINDERS + ".web.server.histogram}") Boolean histogram) {
    return getMeterFilter(histogram, WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS);
  }

  /**
   * Configure new MeterFilter for http.client.requests metrics.
   *
   * @param histogram If a histogram should be published
   * @return A MeterFilter
   */
  @Bean
  @Singleton
  @Requires(property =  MICRONAUT_METRICS_BINDERS + ".web.client.histogram")
  MeterFilter addClientPercentileMeterFilter(@Value("${" + MICRONAUT_METRICS_BINDERS + ".web.client.histogram}") Boolean histogram) {
    return getMeterFilter(histogram, WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS);
  }

  private MeterFilter getMeterFilter(Boolean histogram, String metricNamePrefix) {
    return new MeterFilter() {
      @Override
      public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if (id.getName().startsWith(metricNamePrefix)) {
          return DistributionStatisticConfig.builder()
            .percentilesHistogram(histogram)
            .build()
            .merge(config);
        }
        return config;
      }
    };
  }
}
