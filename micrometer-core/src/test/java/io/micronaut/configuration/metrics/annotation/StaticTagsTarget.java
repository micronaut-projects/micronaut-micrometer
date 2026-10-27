package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Singleton;

/**
 * Methods without tagger or {@code @MeterTag}, whose successful invocations use the tags resolved in advance.
 */
@Singleton
public class StaticTagsTarget {

    @Timed(value = "static.tags.timed", extraTags = {"k1", "v1", "exception", "ignored"})
    @MetricOptions(filterTaggers = true)
    public int timed(boolean fail) {
        if (fail) {
            throw new IllegalStateException("fail");
        }
        return 1;
    }

    @Counted(value = "static.tags.counted", extraTags = {"k1", "v1", "result", "ignored"})
    @MetricOptions(filterTaggers = true)
    public int counted(boolean fail) {
        if (fail) {
            throw new IllegalStateException("fail");
        }
        return 1;
    }

    @Timed(value = "static.tags.invalid", extraTags = {"odd"})
    @MetricOptions(filterTaggers = true)
    public int invalidExtraTags() {
        return 1;
    }
}
