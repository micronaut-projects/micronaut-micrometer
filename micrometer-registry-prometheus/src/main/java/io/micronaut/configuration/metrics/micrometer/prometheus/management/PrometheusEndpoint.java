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
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import io.prometheus.metrics.expositionformats.ExpositionFormatWriter;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.expositionformats.OpenMetrics2TextFormatWriter;
import io.prometheus.metrics.expositionformats.OpenMetricsTextFormatWriter;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Adds a management endpoint for Prometheus.
 *
 * <p>The format of the scrape is negotiated from the {@code Accept} header: the Prometheus text format
 * ({@value #TEXT_FORMAT}), the OpenMetrics text format ({@value #OPENMETRICS_FORMAT}) or the Prometheus protobuf
 * format ({@value #PROTOBUF_FORMAT}). The text format is used when the header asks for none of them.</p>
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

    private static final int MIN_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

    private final PrometheusMeterRegistry prometheusMeterRegistry;
    private final ExpositionFormats expositionFormats;
    private final boolean openMetricsEnabled;
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
        this.expositionFormats = ExpositionFormats.init(prometheusProperties(prometheusConfig));
        this.openMetricsEnabled = openMetricsEnabled;
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

    private ExpositionFormatWriter findWriter(@Nullable String accept) {
        ExpositionFormatWriter writer = expositionFormats.findWriter(accept);
        if (!openMetricsEnabled && (writer instanceof OpenMetricsTextFormatWriter || writer instanceof OpenMetrics2TextFormatWriter)) {
            return expositionFormats.getPrometheusTextFormatWriter();
        }
        return writer;
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
