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
package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig;
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates meter filters that cap the number of distinct {@code uri} tag values of the
 * {@code http.server.requests} and {@code http.client.requests} meters.
 *
 * <p>The guard is opt-in: a filter only exists when
 * {@value #SERVER_MAX_URI_TAGS} or {@value #CLIENT_MAX_URI_TAGS} is set to a positive number.
 * The filters match meters by name, so they apply to the meters of both the HTTP metrics filters
 * of this module and the observation-based HTTP instrumentation. Once the limit is reached, meters
 * with new {@code uri} values are denied and a warning is logged once. Meter filters are only
 * consulted when a meter is first registered, so the guard does not add work to requests whose
 * meters already exist.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Factory
@RequiresMetrics
public class HttpUriTagsMeterFilterFactory {

    /**
     * The maximum number of distinct {@code uri} tag values of the {@code http.server.requests} meters.
     */
    public static final String SERVER_MAX_URI_TAGS = HttpServerMeterConfig.PATH + ".max-uri-tags";

    /**
     * The maximum number of distinct {@code uri} tag values of the {@code http.client.requests} meters.
     */
    public static final String CLIENT_MAX_URI_TAGS = HttpClientMeterConfig.PATH + ".max-uri-tags";

    private static final String URI_TAG = "uri";
    private static final String POSITIVE_NUMBER = "\\s*\\+?0*[1-9][0-9]*\\s*";
    private static final Logger LOG = LoggerFactory.getLogger(HttpUriTagsMeterFilterFactory.class);

    /**
     * Caps the {@code uri} tag values of the {@code http.server.requests} meters.
     *
     * @param maxUriTags The maximum number of distinct values
     * @return The meter filter
     */
    @Singleton
    @Named("httpServerUriTags")
    @Requires(property = SERVER_MAX_URI_TAGS, pattern = POSITIVE_NUMBER)
    MeterFilter httpServerUriTagsMeterFilter(@Property(name = SERVER_MAX_URI_TAGS) int maxUriTags) {
        return maximumUriTags(HttpServerMeterConfig.REQUESTS_METRIC, SERVER_MAX_URI_TAGS, maxUriTags);
    }

    /**
     * Caps the {@code uri} tag values of the {@code http.client.requests} meters.
     *
     * @param maxUriTags The maximum number of distinct values
     * @return The meter filter
     */
    @Singleton
    @Named("httpClientUriTags")
    @Requires(property = CLIENT_MAX_URI_TAGS, pattern = POSITIVE_NUMBER)
    MeterFilter httpClientUriTagsMeterFilter(@Property(name = CLIENT_MAX_URI_TAGS) int maxUriTags) {
        return maximumUriTags(HttpClientMeterConfig.REQUESTS_METRIC, CLIENT_MAX_URI_TAGS, maxUriTags);
    }

    private static MeterFilter maximumUriTags(String meterName, String property, int maxUriTags) {
        return MeterFilter.maximumAllowableTags(meterName, URI_TAG, maxUriTags, new OnlyOnceLoggingDenyMeterFilter(meterName, property, maxUriTags));
    }

    /**
     * Denies meters and logs a warning the first time it does.
     */
    private static final class OnlyOnceLoggingDenyMeterFilter implements MeterFilter {

        private final AtomicBoolean logged = new AtomicBoolean();
        private final String meterName;
        private final String property;
        private final int maxUriTags;

        private OnlyOnceLoggingDenyMeterFilter(String meterName, String property, int maxUriTags) {
            this.meterName = meterName;
            this.property = property;
            this.maxUriTags = maxUriTags;
        }

        @Override
        public MeterFilterReply accept(Meter.Id id) {
            if (LOG.isWarnEnabled() && logged.compareAndSet(false, true)) {
                LOG.warn("Reached the maximum number of '{}' tags ({}) for '{}'. Meters with new '{}' values are not registered. "
                    + "Check that requests are matched to URI templates, or raise the limit with '{}'.",
                    URI_TAG, maxUriTags, meterName, URI_TAG, property);
            }
            return MeterFilterReply.DENY;
        }
    }
}
