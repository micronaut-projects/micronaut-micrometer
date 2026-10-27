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
package io.micronaut.configuration.metrics.micrometer.prometheus.management;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.prometheus.PrometheusMeterRegistryFactory;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.prometheus.metrics.config.OpenMetrics2Properties;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import io.prometheus.metrics.expositionformats.ExpositionFormatWriter;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.expositionformats.OpenMetrics2TextFormatWriter;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Adds a management endpoint for Prometheus.
 *
 * <p>The format of the scrape is negotiated from the {@code Accept} header: the Prometheus text format
 * ({@value #TEXT_FORMAT}), the OpenMetrics text format ({@value #OPENMETRICS_FORMAT}) or the Prometheus protobuf
 * format ({@value #PROTOBUF_FORMAT}). The quality of each format is that of the most specific media range that
 * matches it, the acceptable format with the highest quality is used, a format with {@code q=0} is never used, and
 * the text format is used when the header accepts none of them.</p>
 *
 * @author graemerocher
 * @since 1.1
 */
@Endpoint(PrometheusEndpoint.ID)
public class PrometheusEndpoint {
    public static final String ID = "prometheus";

    /**
     * Whether the endpoint returns the OpenMetrics format when the {@code Accept} header asks for it. When disabled,
     * the Prometheus text format is returned instead. Default {@code true}.
     *
     * @since 6.2.0
     */
    public static final String OPENMETRICS_ENABLED = PrometheusMeterRegistryFactory.PROMETHEUS_CONFIG + ".endpoint.openmetrics.enabled";

    static final String TEXT_FORMAT = "text/plain; version=0.0.4; charset=utf-8";
    static final String OPENMETRICS_FORMAT = "application/openmetrics-text; version=1.0.0; charset=utf-8";
    static final String PROTOBUF_FORMAT = "application/vnd.google.protobuf; proto=io.prometheus.client.MetricFamily; encoding=delimited";

    private static final String WILDCARD = "*";
    private static final String Q_PARAMETER = "q";
    // a media type is more specific than a type range whatever the number of parameters
    private static final int PARAMETER_SPECIFICITY = 64;
    private static final int MIN_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

    private final PrometheusMeterRegistry prometheusMeterRegistry;
    // the representations of the scrape, the text format first
    private final List<Representation> representations;
    // Size of the previous scrape, used to size the next buffer so large registries do not grow it repeatedly
    private volatile int lastScrapeSize;

    /**
     * @param prometheusMeterRegistry The meter registry
     */
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry) {
        this(prometheusMeterRegistry, PrometheusConfig.DEFAULT, true);
    }

    /**
     * @param prometheusMeterRegistry The meter registry
     * @param prometheusConfig The configuration of the meter registry, which selects the available formats
     * @param openMetricsEnabled Whether the OpenMetrics format can be returned
     * @since 6.2.0
     */
    @Inject
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry,
                              PrometheusConfig prometheusConfig,
                              @Property(name = OPENMETRICS_ENABLED, defaultValue = "true") boolean openMetricsEnabled) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
        this.representations = representations(ExpositionFormats.init(prometheusProperties(prometheusConfig)), openMetricsEnabled);
    }

    /**
     * @param prometheusMeterRegistry The meter registry
     * @param scrapeExecutor Not used. The scrape is written on the thread that handles the request.
     * @deprecated The scrape no longer runs on a separate executor. Use {@link #PrometheusEndpoint(PrometheusMeterRegistry)}.
     */
    @Deprecated(since = "6.2", forRemoval = true)
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry,
                              ExecutorService scrapeExecutor) {
        this(prometheusMeterRegistry);
    }

    /**
     * Scrapes the data.
     *
     * @return the data
     * @deprecated Use the streamed endpoint response instead.
     */
    @Deprecated(since = "6.0")
    public String scrape() {
        return prometheusMeterRegistry.scrape();
    }

    /**
     * Scrapes the data in the Prometheus text format.
     *
     * @return the data
     * @deprecated Use {@link #scrape(String, Set)}, which negotiates the format.
     */
    @Deprecated(since = "6.2")
    public InputStream scrapeStream() {
        return scrape(TEXT_FORMAT, (Set<String>) null).body();
    }

    /**
     * Scrapes the data in the format that the {@code Accept} header asks for.
     *
     * <p>The scrape is written to an in-memory buffer on the calling thread, and the response body reads from
     * that buffer. The endpoint runs on the {@link TaskExecutors#BLOCKING} executor so that large scrapes do not
     * block the event loop.</p>
     *
     * @param accept The {@code Accept} header
     * @param includedNames The names of the Prometheus metrics to include, such as {@code jvm_memory_used_bytes}.
     * All the metrics are included when it is not set.
     * @return the data, with the content type of the format
     * @since 6.2.0
     */
    @Read(produces = {TEXT_FORMAT, OPENMETRICS_FORMAT, PROTOBUF_FORMAT})
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<InputStream> scrape(@Header(HttpHeaders.ACCEPT) @Nullable String accept,
                                            @QueryValue @Nullable Set<String> includedNames) {
        ExpositionFormatWriter writer = findWriter(accept);
        ScrapeBuffer buffer = new ScrapeBuffer(initialBufferSize(lastScrapeSize));
        try {
            // the registry selects the same writer from its content type, as its formats use the same properties
            prometheusMeterRegistry.scrape(buffer, writer.getContentType(),
                includedNames == null || includedNames.isEmpty() ? null : includedNames);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scrape Prometheus meter registry", e);
        }
        lastScrapeSize = buffer.size();
        return HttpResponse.ok(buffer.toInputStream()).contentType(writer.getContentType());
    }

    /**
     * Negotiates the format as in RFC 9110 (sections 12.4.2 and 12.5.1): the quality of each available representation
     * is the quality of the most specific media range of the {@code Accept} header that matches it, a representation
     * whose quality is {@code 0} is not acceptable, and the acceptable representation with the highest quality is used.
     * For equal qualities the representation whose range comes first in the header is used, then the first of text,
     * OpenMetrics 1, OpenMetrics 2 and protobuf.
     *
     * @param accept The {@code Accept} header
     * @return The writer, the Prometheus text format when the header accepts none of the representations
     */
    private ExpositionFormatWriter findWriter(@Nullable String accept) {
        Representation text = representations.get(0);
        if (accept == null || accept.isBlank()) {
            return text.writer();
        }
        List<MediaRange> ranges = MediaRange.parseAll(accept);
        Representation best = null;
        MediaRange bestRange = null;
        for (Representation representation : representations) {
            MediaRange range = representation.mostSpecificRange(ranges);
            if (isPreferred(range, bestRange)) {
                best = representation;
                bestRange = range;
            }
        }
        return best != null ? best.writer() : text.writer();
    }

    /**
     * @param range The most specific range of a representation, or {@code null} if no range matches it
     * @param best  The range of the representation selected so far, or {@code null}
     * @return Whether the representation is acceptable and preferred to the one selected so far: its quality is
     * higher, or equal and its range comes first in the header
     */
    private static boolean isPreferred(@Nullable MediaRange range, @Nullable MediaRange best) {
        if (range == null || range.quality().signum() <= 0) {
            return false;
        }
        if (best == null) {
            return true;
        }
        int comparison = range.quality().compareTo(best.quality());
        return comparison > 0 || comparison == 0 && range.index() < best.index();
    }

    /**
     * @param expositionFormats  The formats of the registry
     * @param openMetricsEnabled Whether the OpenMetrics formats can be returned
     * @return The representations, in the order of preference for equal qualities
     */
    private static List<Representation> representations(ExpositionFormats expositionFormats, boolean openMetricsEnabled) {
        List<Representation> representations = new ArrayList<>(4);
        representations.add(Representation.of(expositionFormats.getPrometheusTextFormatWriter()));
        if (openMetricsEnabled) {
            OpenMetrics2TextFormatWriter openMetrics2 = expositionFormats.getOpenMetrics2TextFormatWriter();
            OpenMetrics2Properties openMetrics2Properties = openMetrics2.getOpenMetrics2Properties();
            // without content negotiation OpenMetrics 2 replaces OpenMetrics 1, see ExpositionFormats#findWriter
            if (!openMetrics2Properties.getEnabled() || openMetrics2Properties.getContentNegotiation()) {
                representations.add(Representation.of(expositionFormats.getOpenMetricsTextFormatWriter()));
            }
            if (openMetrics2Properties.getEnabled()) {
                representations.add(Representation.of(openMetrics2));
            }
        }
        representations.add(Representation.of(expositionFormats.getPrometheusProtobufWriter()));
        return List.copyOf(representations);
    }

    private static PrometheusProperties prometheusProperties(PrometheusConfig prometheusConfig) {
        // the same properties as PrometheusMeterRegistry, so that the formats match those of the registry
        Properties properties = prometheusConfig.prometheusProperties();
        return properties != null ? PrometheusPropertiesLoader.load(properties) : PrometheusPropertiesLoader.load();
    }

    /**
     * Sizes the buffer for the next scrape from the size of the previous one, with some headroom for new meters.
     *
     * @param previousSize The size of the previous scrape
     * @return The initial buffer size
     */
    static int initialBufferSize(int previousSize) {
        long size = (long) previousSize + (previousSize >> 3);
        return (int) Math.min(MAX_BUFFER_SIZE, Math.max(MIN_BUFFER_SIZE, size));
    }

    /**
     * A representation of the scrape: a writer and the media type of its content.
     *
     * @param writer     The writer
     * @param mediaType  The media type of the content of the writer
     */
    private record Representation(ExpositionFormatWriter writer, MediaRange mediaType) {

        static Representation of(ExpositionFormatWriter writer) {
            return new Representation(writer, Objects.requireNonNull(MediaRange.parse(writer.getContentType(), 0)));
        }

        @Nullable MediaRange mostSpecificRange(List<MediaRange> ranges) {
            MediaRange result = null;
            int specificity = -1;
            for (MediaRange range : ranges) {
                int rangeSpecificity = specificity(range);
                if (rangeSpecificity > specificity) {
                    result = range;
                    specificity = rangeSpecificity;
                }
            }
            return result;
        }

        /**
         * A range matches when its type and subtype match, or are wildcards, and each of its parameters that the
         * representation has, such as {@code version} or {@code charset}, has the same value, ignoring case. The
         * parameters that the representation does not have, such as {@code escaping}, do not select a representation.
         *
         * @param range A media range
         * @return The specificity of the range if it matches, otherwise {@code -1}: {@code *}{@code /*} is less specific
         * than a type range such as {@code text/*}, which is less specific than a media type, which is less specific than
         * a media type with more matching parameters
         */
        int specificity(MediaRange range) {
            int specificity;
            if (WILDCARD.equals(range.type())) {
                specificity = 0;
            } else if (!range.type().equals(mediaType.type())) {
                return -1;
            } else if (WILDCARD.equals(range.subtype())) {
                specificity = 1;
            } else if (range.subtype().equals(mediaType.subtype())) {
                specificity = 2;
            } else {
                return -1;
            }
            int matchedParameters = 0;
            for (Map.Entry<String, String> parameter : range.parameters().entrySet()) {
                String value = mediaType.parameters().get(parameter.getKey());
                if (value != null) {
                    if (!value.equalsIgnoreCase(parameter.getValue())) {
                        return -1;
                    }
                    matchedParameters++;
                }
            }
            return specificity * PARAMETER_SPECIFICITY + matchedParameters;
        }
    }

    /**
     * A media range of the {@code Accept} header, or the media type of a representation.
     *
     * @param type       The type, in lower case
     * @param subtype    The subtype, in lower case
     * @param parameters The parameters other than the quality, with their names in lower case
     * @param quality    The quality, {@code 1} when the range has no {@code q} parameter (its name is case-insensitive)
     * @param index      The position of the range in the header
     */
    private record MediaRange(String type, String subtype, Map<String, String> parameters, BigDecimal quality, int index) {

        /**
         * @param accept The {@code Accept} header
         * @return The valid media ranges, in the order of the header
         */
        static List<MediaRange> parseAll(String accept) {
            List<MediaRange> ranges = new ArrayList<>();
            for (String value : accept.split(",")) {
                MediaRange range = parse(value, ranges.size());
                if (range != null) {
                    ranges.add(range);
                }
            }
            return ranges;
        }

        /**
         * @param value A media range
         * @param index The position of the range in the header
         * @return The range, or {@code null} if it is invalid
         */
        static @Nullable MediaRange parse(String value, int index) {
            String[] parts = value.split(";");
            String name = parts[0].trim().toLowerCase(Locale.ROOT);
            int slash = name.indexOf('/');
            if (slash <= 0 || slash == name.length() - 1) {
                return null;
            }
            Map<String, String> parameters = new HashMap<>();
            BigDecimal quality = BigDecimal.ONE;
            for (int i = 1; i < parts.length; i++) {
                int equals = parts[i].indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String parameterName = parts[i].substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String parameterValue = unquote(parts[i].substring(equals + 1).trim());
                if (Q_PARAMETER.equals(parameterName)) {
                    quality = quality(parameterValue);
                    if (quality == null) {
                        return null;
                    }
                } else {
                    parameters.put(parameterName, parameterValue);
                }
            }
            return new MediaRange(name.substring(0, slash), name.substring(slash + 1), parameters, quality, index);
        }

        private static @Nullable BigDecimal quality(String value) {
            try {
                BigDecimal quality = new BigDecimal(value);
                return quality.signum() < 0 || quality.compareTo(BigDecimal.ONE) > 0 ? null : quality;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static String unquote(String value) {
            return value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'
                ? value.substring(1, value.length() - 1)
                : value;
        }
    }

    /**
     * A {@link ByteArrayOutputStream} that exposes its content as an {@link InputStream} without copying it.
     */
    private static final class ScrapeBuffer extends ByteArrayOutputStream {
        ScrapeBuffer(int size) {
            super(size);
        }

        InputStream toInputStream() {
            return new ByteArrayInputStream(buf, 0, count);
        }
    }
}
