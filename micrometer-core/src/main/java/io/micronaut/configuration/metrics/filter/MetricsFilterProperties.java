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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.core.convert.format.MapFormat.MapTransformation.FLAT;
import static io.micronaut.core.naming.conventions.StringConvention.RAW;

/**
 * Meter filter configuration keyed by meter name prefix.
 *
 * <p>Every map in this class is keyed by a meter name prefix. The most specific (longest) prefix
 * that matches a meter name wins, so {@code http.server} applies to {@code http.server.requests}
 * unless {@code http.server.requests} is configured too. Where noted, the key {@code all} applies
 * to every meter that no other key matches.</p>
 *
 * <pre>
 * micronaut.metrics.enable.jvm.gc=false
 * micronaut.metrics.distribution.percentiles-histogram.http.server.requests=true
 * micronaut.metrics.distribution.slo.http.server.requests=100ms,500ms
 * </pre>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@ConfigurationProperties(MetricsFilterProperties.PREFIX)
public class MetricsFilterProperties {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "micronaut.metrics";

    /**
     * The prefix of the per meter enable flags.
     */
    public static final String ENABLE_PREFIX = PREFIX + ".enable";

    /**
     * The prefix of the per meter distribution settings.
     */
    public static final String DISTRIBUTION_PREFIX = PREFIX + ".distribution";

    /**
     * The key that applies to all meters.
     */
    public static final String ALL = "all";

    private Map<String, Boolean> enable = new LinkedHashMap<>();

    private final Distribution distribution;

    /**
     * @param distribution The distribution settings
     */
    public MetricsFilterProperties(Distribution distribution) {
        this.distribution = distribution;
    }

    /**
     * @return Whether meters whose names start with the given prefix are enabled.
     */
    public Map<String, Boolean> getEnable() {
        return enable;
    }

    /**
     * Whether meters whose names start with the given prefix are enabled. The most specific prefix
     * wins and the key {@code all} applies to every meter.
     *
     * @param enable The enable flags keyed by meter name prefix
     */
    public void setEnable(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, Boolean> enable) {
        this.enable = enable;
    }

    /**
     * @return The distribution settings
     */
    public Distribution getDistribution() {
        return distribution;
    }

    /**
     * Distribution statistic settings for timers and distribution summaries, keyed by meter name
     * prefix.
     *
     * @since 6.2.0
     */
    @ConfigurationProperties("distribution")
    public static class Distribution {

        private Map<String, Boolean> percentilesHistogram = new LinkedHashMap<>();
        private Map<String, List<Double>> percentiles = new LinkedHashMap<>();
        private Map<String, List<String>> slo = new LinkedHashMap<>();
        private Map<String, String> minimumExpectedValue = new LinkedHashMap<>();
        private Map<String, String> maximumExpectedValue = new LinkedHashMap<>();
        private Map<String, Duration> expiry = new LinkedHashMap<>();
        private Map<String, Integer> bufferLength = new LinkedHashMap<>();

        /**
         * @return Whether to publish a histogram suitable for computing aggregable percentiles.
         */
        public Map<String, Boolean> getPercentilesHistogram() {
            return percentilesHistogram;
        }

        /**
         * Whether meters whose names start with the given prefix should publish a histogram
         * suitable for computing aggregable (across dimensions) percentile approximations. The key
         * {@code all} applies to every meter.
         *
         * @param percentilesHistogram The flags keyed by meter name prefix
         */
        public void setPercentilesHistogram(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, Boolean> percentilesHistogram) {
            this.percentilesHistogram = percentilesHistogram;
        }

        /**
         * @return The client-side percentiles to publish
         */
        public Map<String, List<Double>> getPercentiles() {
            return percentiles;
        }

        /**
         * Client-side computed percentiles to publish for meters whose names start with the given
         * prefix, for example {@code 0.5,0.95,0.99}. These are not aggregable across dimensions.
         * The key {@code all} applies to every meter.
         *
         * @param percentiles The percentiles keyed by meter name prefix
         */
        public void setPercentiles(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, List<Double>> percentiles) {
            this.percentiles = percentiles;
        }

        /**
         * @return The service level objective boundaries
         */
        public Map<String, List<String>> getSlo() {
            return slo;
        }

        /**
         * Service level objective boundaries for meters whose names start with the given prefix.
         * Timer values are durations such as {@code 100ms} (a plain number is read as
         * milliseconds) and distribution summary values are numbers. The key {@code all} is not
         * supported because the unit depends on the meter type.
         *
         * @param slo The boundaries keyed by meter name prefix
         */
        public void setSlo(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, List<String>> slo) {
            this.slo = slo;
        }

        /**
         * @return The minimum expected values
         */
        public Map<String, String> getMinimumExpectedValue() {
            return minimumExpectedValue;
        }

        /**
         * The minimum value that meters whose names start with the given prefix are expected to
         * observe. It bounds the histogram buckets that are shipped. Timer values are durations and
         * distribution summary values are numbers.
         *
         * @param minimumExpectedValue The values keyed by meter name prefix
         */
        public void setMinimumExpectedValue(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, String> minimumExpectedValue) {
            this.minimumExpectedValue = minimumExpectedValue;
        }

        /**
         * @return The maximum expected values
         */
        public Map<String, String> getMaximumExpectedValue() {
            return maximumExpectedValue;
        }

        /**
         * The maximum value that meters whose names start with the given prefix are expected to
         * observe. It bounds the histogram buckets that are shipped. Timer values are durations and
         * distribution summary values are numbers.
         *
         * @param maximumExpectedValue The values keyed by meter name prefix
         */
        public void setMaximumExpectedValue(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, String> maximumExpectedValue) {
            this.maximumExpectedValue = maximumExpectedValue;
        }

        /**
         * @return How long samples are accumulated in decaying distribution statistics
         */
        public Map<String, Duration> getExpiry() {
            return expiry;
        }

        /**
         * Maximum amount of time that samples for meters whose names start with the given prefix
         * are accumulated to decaying distribution statistics before they are reset and rotated.
         * The key {@code all} applies to every meter.
         *
         * @param expiry The expiry keyed by meter name prefix
         */
        public void setExpiry(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, Duration> expiry) {
            this.expiry = expiry;
        }

        /**
         * @return The number of histograms in the ring buffer
         */
        public Map<String, Integer> getBufferLength() {
            return bufferLength;
        }

        /**
         * Number of histograms for meters whose names start with the given prefix to keep in the
         * ring buffer. The key {@code all} applies to every meter.
         *
         * @param bufferLength The buffer length keyed by meter name prefix
         */
        public void setBufferLength(@MapFormat(keyFormat = RAW, transformation = FLAT) Map<String, Integer> bufferLength) {
            this.bufferLength = bufferLength;
        }
    }
}
