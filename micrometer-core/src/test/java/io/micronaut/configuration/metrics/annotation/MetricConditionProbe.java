package io.micronaut.configuration.metrics.annotation;

import jakarta.inject.Singleton;

/**
 * Enables the metrics of {@link ConditionExpressionTarget}, whose tag expressions fail while
 * the metrics are disabled.
 */
@Singleton
public class MetricConditionProbe {

    private volatile boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String tag() {
        if (!enabled) {
            throw new IllegalStateException("A tag expression was evaluated while the condition is false");
        }
        return "ok";
    }
}
