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
package io.micronaut.configuration.metrics.filter;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static io.micronaut.configuration.metrics.filter.MetricsFilterProperties.ALL;
import static io.micronaut.configuration.metrics.filter.MetricsFilterProperties.DISTRIBUTION;

/**
 * A {@link MeterFilter} that applies the {@code micronaut.metrics.enable.*} and
 * {@code micronaut.metrics.distribution.*} configuration.
 *
 * <p>The bean only exists when at least one of those properties is set. Values are resolved once
 * when the filter is created, and Micrometer only consults meter filters when a meter is first
 * registered, so the filter adds no cost when meters are recorded.</p>
 *
 * <p>The filter is ordered late so that the settings it applies take precedence over earlier
 * filters, such as the {@code micronaut.metrics.binders.web.server.*} shortcut.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Singleton
@RequiresMetrics
@Requires(condition = PropertiesMeterFilterCondition.class)
public class PropertiesMeterFilter implements MeterFilter, Ordered {

    /**
     * The order of the filter.
     */
    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private final Map<String, Boolean> enable;
    private final Map<String, Boolean> percentilesHistogram;
    private final Map<String, double[]> percentiles;
    private final Map<String, MeterValue[]> slo;
    private final Map<String, MeterValue> minimumExpectedValue;
    private final Map<String, MeterValue> maximumExpectedValue;
    private final Map<String, Duration> expiry;
    private final Map<String, Integer> bufferLength;
    private final boolean hasDistribution;

    /**
     * @param properties        The configuration
     * @param conversionService The conversion service used to parse durations
     */
    public PropertiesMeterFilter(MetricsFilterProperties properties, ConversionService conversionService) {
        MetricsFilterProperties.Distribution distribution = properties.getDistribution();
        this.enable = transform(properties.getEnable(), (key, value) -> value);
        this.percentilesHistogram = transform(distribution.getPercentilesHistogram(), (key, value) -> value);
        this.percentiles = transform(distribution.getPercentiles(), (key, values) -> values.stream()
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .toArray());
        this.slo = transform(distribution.getSlo(), (key, values) -> parseAll(DISTRIBUTION + ".slo." + key, values, conversionService));
        this.minimumExpectedValue = transform(distribution.getMinimumExpectedValue(),
            (key, value) -> MeterValue.parse(DISTRIBUTION + ".minimum-expected-value." + key, value, conversionService));
        this.maximumExpectedValue = transform(distribution.getMaximumExpectedValue(),
            (key, value) -> MeterValue.parse(DISTRIBUTION + ".maximum-expected-value." + key, value, conversionService));
        this.expiry = transform(distribution.getExpiry(), (key, value) -> value);
        this.bufferLength = transform(distribution.getBufferLength(), (key, value) -> value);
        this.hasDistribution = !percentilesHistogram.isEmpty() || !percentiles.isEmpty() || !slo.isEmpty()
            || !minimumExpectedValue.isEmpty() || !maximumExpectedValue.isEmpty() || !expiry.isEmpty() || !bufferLength.isEmpty();
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        Boolean enabled = lookupWithFallbackToAll(enable, id);
        return enabled == null || enabled ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY;
    }

    @Override
    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if (!hasDistribution) {
            return config;
        }
        Meter.Type type = id.getType();
        return DistributionStatisticConfig.builder()
            .percentilesHistogram(lookupWithFallbackToAll(percentilesHistogram, id))
            .percentiles(lookupWithFallbackToAll(percentiles, id))
            .serviceLevelObjectives(toServiceLevelObjectives(type, lookup(slo, id)))
            .minimumExpectedValue(toValue(type, lookup(minimumExpectedValue, id)))
            .maximumExpectedValue(toValue(type, lookup(maximumExpectedValue, id)))
            .expiry(lookupWithFallbackToAll(expiry, id))
            .bufferLength(lookupWithFallbackToAll(bufferLength, id))
            .build()
            .merge(config);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static MeterValue[] parseAll(String property, List<String> values, ConversionService conversionService) {
        // a single property value such as "100ms,1s" may not have been split into a list
        return values.stream()
            .filter(Objects::nonNull)
            .flatMap(value -> Arrays.stream(value.split(",")))
            .filter(value -> !value.isBlank())
            .map(value -> MeterValue.parse(property, value, conversionService))
            .toArray(MeterValue[]::new);
    }

    private static double @Nullable [] toServiceLevelObjectives(Meter.Type type, MeterValue @Nullable [] values) {
        if (values == null) {
            return null;
        }
        double[] converted = Arrays.stream(values)
            .map(value -> value.getValue(type))
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .toArray();
        return converted.length == 0 ? null : converted;
    }

    @Nullable
    private static Double toValue(Meter.Type type, @Nullable MeterValue value) {
        return value == null ? null : value.getValue(type);
    }

    @Nullable
    private static <T> T lookupWithFallbackToAll(Map<String, T> values, Meter.Id id) {
        if (values.isEmpty()) {
            return null;
        }
        T value = lookup(values, id);
        return value != null ? value : values.get(ALL);
    }

    /**
     * Finds the value of the most specific prefix of the meter name: {@code a.b.c}, then
     * {@code a.b}, then {@code a}.
     */
    @Nullable
    private static <T> T lookup(Map<String, T> values, Meter.Id id) {
        if (values.isEmpty()) {
            return null;
        }
        String name = id.getName();
        while (!name.isEmpty()) {
            T result = values.get(name);
            if (result != null) {
                return result;
            }
            int lastDot = name.lastIndexOf('.');
            name = lastDot != -1 ? name.substring(0, lastDot) : "";
        }
        return null;
    }

    private static <S, T> Map<String, T> transform(@Nullable Map<String, S> source, BiFunction<String, S, T> function) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, T> result = new HashMap<>(source.size());
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, function.apply(key, value));
            }
        });
        return Map.copyOf(result);
    }
}
