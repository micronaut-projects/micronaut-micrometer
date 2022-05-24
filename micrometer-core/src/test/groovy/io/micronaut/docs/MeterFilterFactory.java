package io.micronaut.docs;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Collections;

@Factory
public class MeterFilterFactory {

    /**
     * Add global tags to all metrics.
     *
     * @return meter filter
     */
    @Bean
    @Singleton
    MeterFilter addCommonTagFilter() {
        return MeterFilter.commonTags(Collections.singletonList(Tag.of("scope", "demo")));
    }

    /**
     * Rename a tag key for every metric beginning with a given prefix.
     * <p>
     * This will rename the metric name http.server.requests tag value called `method` to `httpmethod`
     * <p>
     * OLD: http.server.requests ['method':'GET", ...]
     * NEW: http.server.requests ['httpmethod':'GET", ...]
     *
     * @return meter filter
     */
    @Bean
    @Singleton
    MeterFilter renameFilter() {
        return MeterFilter.renameTag("http.server.requests", "method", "httpmethod");
    }
}
