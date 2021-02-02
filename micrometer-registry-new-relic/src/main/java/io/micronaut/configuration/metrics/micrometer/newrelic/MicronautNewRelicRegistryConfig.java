package io.micronaut.configuration.metrics.micrometer.newrelic;

import com.newrelic.telemetry.micrometer.NewRelicRegistryConfig;
import io.micrometer.core.instrument.config.validate.InvalidReason;

import io.micrometer.core.lang.Nullable;

import javax.inject.Singleton;

import static io.micrometer.core.instrument.config.validate.PropertyValidator.getSecret;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;
import static io.micrometer.core.instrument.util.StringUtils.isBlank;

/**
 * Configuration for {@link NewRelicRegistry}.
 *
 */
@Singleton
public interface MicronautNewRelicRegistryConfig extends NewRelicRegistryConfig {

    @Override
    default String prefix() {
        return "newrelic";
    }

    @Nullable
    default String apiKey() {
        return getSecret(this, "apiKey")
                .invalidateWhen(
                        secret -> isBlank(secret),
                        "is required when publishing to Metrics API",
                        InvalidReason.MISSING
                )
                .orElse(null);
    }

    /**
     * @return The URI for the New Relic metric API. Only necessary if you need to override the
     *     default URI (https://metric-api.newrelic.com/metric/v1).
     */
    default String uri() {
        return getUrlString(this, "uri").orElse(null);
    }

    /**
     * Return the service name which this registry will report as. Maps to the "service.name"
     * attribute on the metrics.
     *
     * @return The Service Name.
     */
    default String serviceName() {
        return getString(this, "serviceName").orElse(null);
    }

    /**
     * Turn on "audit mode" in the underlying New Relic Telemetry SDK. This will log all data sent to
     * the New Relic APIs. Be aware that if there is sensitive information in the data being sent that
     * it will be sent to wherever the Telemetry SDK logs are configured to go.
     *
     * @return true if audit mode should be enabled.
     */
    default boolean enableAuditMode() {
        return false;
    }
}
