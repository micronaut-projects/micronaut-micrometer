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
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ForkJoinPool;

/**
 * Adds a management endpoint for Prometheus.
 *
 * @author graemerocher
 * @since 1.1
 */
@Endpoint(PrometheusEndpoint.ID)
public class PrometheusEndpoint {
    public static final String ID = "prometheus";
    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    private final PrometheusMeterRegistry prometheusMeterRegistry;
    private final ExecutorService scrapeExecutor;

    /**
     * @param prometheusMeterRegistry The meter registry
     */
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry) {
        this(prometheusMeterRegistry, ForkJoinPool.commonPool());
    }

    /**
     * @param prometheusMeterRegistry The meter registry
     * @param scrapeExecutor The executor used to stream the scrape
     */
    @Inject
    public PrometheusEndpoint(PrometheusMeterRegistry prometheusMeterRegistry,
                              @Named(TaskExecutors.BLOCKING) ExecutorService scrapeExecutor) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
        this.scrapeExecutor = scrapeExecutor;
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
     * @return the data
     */
    @Read(produces = "text/plain; version=0.0.4")
    public InputStream scrapeStream() {
        ProducerAwareInputStream inputStream = ProducerAwareInputStream.create();
        inputStream.start(scrapeExecutor, prometheusMeterRegistry);
        return inputStream;
    }

    private static final class ProducerAwareInputStream extends FilterInputStream {
        private final PipedOutputStream outputStream;
        private Future<?> writeFuture;

        ProducerAwareInputStream(PipedInputStream inputStream) throws IOException {
            super(inputStream);
            this.outputStream = new PipedOutputStream(inputStream);
        }

        static ProducerAwareInputStream create() {
            PipedInputStream inputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
            try {
                return new ProducerAwareInputStream(inputStream);
            } catch (IOException e) {
                try {
                    inputStream.close();
                } catch (IOException closeException) {
                    e.addSuppressed(closeException);
                }
                throw new IllegalStateException("Failed to create Prometheus scrape stream", e);
            }
        }

        void start(ExecutorService scrapeExecutor, PrometheusMeterRegistry prometheusMeterRegistry) {
            try {
                writeFuture = scrapeExecutor.submit(() -> writeScrape(prometheusMeterRegistry));
            } catch (RuntimeException e) {
                try {
                    close();
                } catch (IOException closeException) {
                    e.addSuppressed(closeException);
                }
                throw e;
            }
        }

        private void writeScrape(PrometheusMeterRegistry prometheusMeterRegistry) {
            try (PipedOutputStream stream = outputStream) {
                prometheusMeterRegistry.scrape(stream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public int read() throws IOException {
            int read = super.read();
            verifyProducer(read);
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            verifyProducer(read);
            return read;
        }

        @Override
        public void close() throws IOException {
            IOException closeException = null;
            try {
                super.close();
            } catch (IOException e) {
                closeException = e;
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                if (closeException == null) {
                    closeException = e;
                } else {
                    closeException.addSuppressed(e);
                }
            }
            if (writeFuture != null) {
                writeFuture.cancel(true);
            }
            if (closeException != null) {
                throw closeException;
            }
        }

        private void verifyProducer(int read) throws IOException {
            if (read == -1) {
                awaitProducer();
            }
        }

        private void awaitProducer() throws IOException {
            try {
                writeFuture.get();
            } catch (CancellationException ignored) {
                // The consumer closed the stream early.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while streaming Prometheus scrape", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof UncheckedIOException uncheckedIOException) {
                    throw uncheckedIOException.getCause();
                }
                throw new IOException("Failed to stream Prometheus scrape", cause);
            }
        }
    }
}
