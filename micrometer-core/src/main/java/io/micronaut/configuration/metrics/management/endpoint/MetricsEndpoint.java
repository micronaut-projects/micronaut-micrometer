/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.configuration.metrics.management.endpoint;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.type.Argument;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.micronaut.core.annotation.TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS;
import static io.micronaut.core.annotation.TypeHint.AccessType.ALL_PUBLIC_METHODS;

/**
 * Provides an endpoint to visualize metrics.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Endpoint(value = MetricsEndpoint.NAME, defaultSensitive = MetricsEndpoint.DEFAULT_SENSITIVE)
@RequiresMetrics
@TypeHint(value = {
        MetricsEndpoint.class,
        MetricsEndpoint.MetricNames.class,
        MetricsEndpoint.MetricDetails.class,
        MetricsEndpoint.AvailableTag.class,
        MetricsEndpoint.Sample.class},
        accessType = {ALL_DECLARED_CONSTRUCTORS, ALL_PUBLIC_METHODS})
public class MetricsEndpoint {

    /**
     * If the endpoint is sensitive if no configuration is provided.
     */
    static final boolean DEFAULT_SENSITIVE = false;

    /**
     * Constant for metrics.
     */
    static final String NAME = "metrics";

    private final MeterRegistry meterRegistry;

    /**
     * @param meterRegistry The meter registry
     * @param dataSources   To ensure data sources are loaded
     */
    public MetricsEndpoint(MeterRegistry meterRegistry,
                           DataSource[] dataSources) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Lists metric names. To get the details, invoke the method {@code getMetricDetails(name)}.
     *
     * @return single of HTTP response with metric names
     */
    @Read
    public MetricNames listNames() {
        return new MetricNames(
                meterRegistry.getMeters().stream()
                        .map(this::getName)
                        .collect(TreeSet::new, TreeSet::add, TreeSet::addAll));
    }

    /**
     * Reads individual metric data.
     * <p>
     * After calling the /metrics endpoint, you can pass the name in
     * like /metrics/foo.bar and the details for the metrics and tags
     * will be returned.
     * <p>
     * Will return a 404 if the metric is not found.
     *
     * @param name the name of the metric to get the details for
     * @param tag  The tags
     * @return single with metric details response
     */
    @Read
    public MetricDetails getMetricDetails(@Selector String name,
                                          @Nullable List<String> tag) {
        return getMetricDetailsResponse(name, tag);
    }

    /**
     * Read individual metric data.
     * <p>
     * After calling the /metrics endpoint, you can pass the name in
     * like /metrics/foo.bar and the details for the metrics and tags
     * will be returned.
     * <p>
     * Will return a 404 if the metric is not found.
     *
     * @param name     the name of the meter to get the details for.
     * @param tagNames The tags
     * @return single with metric details response
     */
    private MetricDetails getMetricDetailsResponse(String name, List<String> tagNames) {
        List<Tag> tags = tagNames == null ? Collections.emptyList() : tagNames.stream().map(s -> {
            if (s.contains(":")) {
                String[] tv = s.split(":");
                if (tv.length == 2) {
                    return Tag.of(tv[0], tv[1]);
                }
            }
            throw new UnsatisfiedArgumentException(Argument.of(List.class, "tags"), "Tags must be in the form key:value");
        }).collect(Collectors.toList());

        Collection<Meter> meters = meterRegistry.find(name).tags(tags).meters();
        if (meters.isEmpty()) {
            return null;
        }
        Map<Statistic, Double> samples = getSamples(meters);
        Map<String, Set<String>> availableTags = getAvailableTags(meters);
        for (Tag t : tags) {
            availableTags.remove(t.getKey());
        }
        Meter.Id meterId = meters.iterator().next().getId();
        return new MetricDetails(name,
                asList(samples, Sample::new),
                asList(availableTags, AvailableTag::new),
                meterId.getDescription(),
                meterId.getBaseUnit());
    }

    private Map<Statistic, Double> getSamples(Collection<Meter> meters) {
        Map<Statistic, Double> samples = new LinkedHashMap<>();
        for (Meter meter : meters) {
            mergeMeasurements(samples, meter);
        }
        return samples;
    }

    private void mergeMeasurements(Map<Statistic, Double> samples, Meter meter) {
        for (Measurement measurement : meter.measure()) {
            samples.merge(measurement.getStatistic(),
                    measurement.getValue(), mergeFunction(measurement.getStatistic()));
        }
    }

    private BiFunction<Double, Double, Double> mergeFunction(Statistic statistic) {
        return (Statistic.MAX.equals(statistic) ? Double::max : Double::sum);
    }

    /**
     * Get all the available tags.
     *
     * @param meters meters to iterate
     * @return map of the tags
     */
    private Map<String, Set<String>> getAvailableTags(Collection<Meter> meters) {
        Map<String, Set<String>> availableTags = new HashMap<>();
        for (Meter meter : meters) {
            mergeAvailableTags(availableTags, meter);
        }
        return availableTags;
    }

    /**
     * Merge the tags from across all the meters.
     *
     * @param availableTags all the tags
     * @param meter         the meter to get tags from
     */
    private void mergeAvailableTags(Map<String, Set<String>> availableTags, Meter meter) {
        for (Tag tag : meter.getId().getTags()) {
            Set<String> value = Collections.singleton(tag.getValue());
            availableTags.merge(tag.getKey(), value, this::merge);
        }
    }

    /**
     * Merge two sets.
     *
     * @param set1 first set
     * @param set2 second set
     * @param <T>  Type
     * @return merged set
     */
    private <T> Set<T> merge(Set<T> set1, Set<T> set2) {
        Set<T> result = new HashSet<>(set1.size() + set2.size());
        result.addAll(set1);
        result.addAll(set2);
        return result;
    }

    private <K, V, T> List<T> asList(Map<K, V> map, BiFunction<K, V, T> mapper) {
        return map.entrySet().stream()
                .map((entry) -> mapper.apply(entry.getKey(), entry.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Get the meter name.
     *
     * @param meter Meter
     * @return name of the meter
     */
    private String getName(Meter meter) {
        return meter.getId().getName();
    }

    /**
     * Response payload for a metric name listing.
     */
    @Introspected
    public static final class MetricNames {

        private final SortedSet<String> names;

        /**
         * Object to hold metric names.
         *
         * @param names list of names
         */
        MetricNames(SortedSet<String> names) {
            this.names = names;
        }

        /**
         * Get the names.
         *
         * @return set of names
         */
        public SortedSet<String> getNames() {
            return names;
        }
    }

    /**
     * Response payload for a metric name selector.
     */
    @Introspected
    public static final class MetricDetails {

        private final String name;

        private final List<Sample> measurements;

        private final List<AvailableTag> availableTags;

        private final String description;

        private final String baseUnit;

        /**
         * Object to hold metric response for name, value and tags.
         *
         * @param name          the name
         * @param measurements  numerical values
         * @param availableTags tags
         * @param description   description of the metric
         * @param baseUnit      metric base unit
         */
        MetricDetails(String name,
                      List<Sample> measurements,
                      List<AvailableTag> availableTags,
                      String description,
                      String baseUnit) {
            this.name = name;
            this.measurements = measurements;
            this.availableTags = availableTags;
            this.description = description;
            this.baseUnit = baseUnit;
        }

        /**
         * Get the name.
         *
         * @return name
         */
        public String getName() {
            return name;
        }

        /**
         * Get measurement.
         *
         * @return list of measurements
         */
        public List<Sample> getMeasurements() {
            return measurements;
        }

        /**
         * Get tags.
         *
         * @return list of tags
         */
        public List<AvailableTag> getAvailableTags() {
            return availableTags;
        }

        /**
         * Get description.
         *
         * @return metric description
         */
        public String getDescription() {
            return description;
        }

        /**
         * Get base unit.
         *
         * @return metric base unit
         */
        public String getBaseUnit() {
            return baseUnit;
        }

    }

    /**
     * A set of tags for further dimensional drilldown and their potential values.
     */
    @Introspected
    public static final class AvailableTag {

        private final String tag;

        private final Set<String> values;

        /**
         * Available tags for a metric.
         *
         * @param tag    tag name
         * @param values tag values
         */
        AvailableTag(String tag, Set<String> values) {
            this.tag = tag;
            this.values = values;
        }

        /**
         * Get tag name.
         *
         * @return tag name
         */
        public String getTag() {
            return tag;
        }

        /**
         * Get tag values.
         *
         * @return list of tag values
         */
        public Set<String> getValues() {
            return values;
        }

    }

    /**
     * A measurement sample combining a {@link Statistic statistic} and a value.
     */
    @Introspected
    public static final class Sample {

        private final Statistic statistic;

        private final Double value;

        /**
         * Numerical sample of the metrics.
         *
         * @param statistic measurement name
         * @param value     measurement value
         */
        Sample(Statistic statistic, Double value) {
            this.statistic = statistic;
            this.value = value;
        }

        /**
         * Measurement name.
         *
         * @return measurement name
         */
        public Statistic getStatistic() {
            return statistic;
        }

        /**
         * Measurement value.
         *
         * @return measurement value
         */
        public Double getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "MeasurementSample{" + "statistic=" + statistic + ", value=" + value + '}';
        }
    }
}
