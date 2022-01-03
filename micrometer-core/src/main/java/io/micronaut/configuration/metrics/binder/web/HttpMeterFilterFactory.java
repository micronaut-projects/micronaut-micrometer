package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.binder.web.WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS;
import static io.micronaut.configuration.metrics.binder.web.WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

@Factory
@RequiresMetrics
@Requires(property = WebMetricsPublisher.ENABLED, notEquals = StringUtils.FALSE)
public class HttpMeterFilterFactory {


    @Bean
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".web.server.percentiles")
    MeterFilter addServerPercentileMeterFilter(@Value("${" + MICRONAUT_METRICS_BINDERS + ".web.server.percentiles}") Double[] percentiles) {
        return getMeterFilter(percentiles, METRIC_HTTP_SERVER_REQUESTS);
    }

    @Bean
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".web.client.percentiles")
    MeterFilter addClientPercentileMeterFilter(@Value("${" + MICRONAUT_METRICS_BINDERS + ".web.client.percentiles}") Double[] percentiles) {
        return getMeterFilter(percentiles, METRIC_HTTP_CLIENT_REQUESTS);
    }

    private MeterFilter getMeterFilter(Double[] percentiles, String metricNamePrefix) {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith(metricNamePrefix)) {
                    return DistributionStatisticConfig.builder()
                            .percentiles((double[]) ArrayUtils.toPrimitiveArray(percentiles))
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }

}
