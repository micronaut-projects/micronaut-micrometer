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

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;

/**
 * Adds a management endpoint for Prometheus.
 *
 * @author graemerocher
 * @since 1.1
 */
@Endpoint(PrometheusEndpoint.ID)
public class PrometheusEndpoint {
    public static final String ID = "prometheus";
    private static final int MIN_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

    private final PrometheusMeterRegistry prometheusMeterRegistry;
    // Size of the previous scrape, used to size the next buffer so large registries do not grow it repeatedly
    private volatile int lastScrapeSize;

    /**
     * @param prometheusMeterRegistry The meter registry
     */
    @Inject
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
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
     * Scrapes the data.
     *
     * <p>The scrape is written to an in-memory buffer on the calling thread, and the returned stream reads from
     * that buffer. The endpoint runs on the {@link TaskExecutors#BLOCKING} executor so that large scrapes do not
     * block the event loop.</p>
     *
     * @return the data
     */
    @Read(produces = "text/plain; version=0.0.4")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public InputStream scrapeStream() {
        ScrapeBuffer buffer = new ScrapeBuffer(initialBufferSize(lastScrapeSize));
        try {
            prometheusMeterRegistry.scrape(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scrape Prometheus meter registry", e);
        }
        lastScrapeSize = buffer.size();
        return buffer.toInputStream();
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
