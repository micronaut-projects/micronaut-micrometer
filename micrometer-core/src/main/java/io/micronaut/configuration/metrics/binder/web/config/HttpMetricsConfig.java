/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.configuration.metrics.binder.web.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * The http config.
 *
 * @author Denis Stepanov
 * @since 5.9
 */
@ConfigurationProperties(HttpMetricsConfig.PATH)
public final class HttpMetricsConfig implements Toggleable {

    /**
     * To config path.
     */
    public static final String PATH = MICRONAUT_METRICS_BINDERS + ".web";

    private boolean enabled = true;

    /**
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The errors config.
     *
     * @param enabled Is enabled
     */
    @ConfigurationProperties("client-errors-uris")
    public record ClientErrorsUrisConfig(@Bindable(defaultValue = "true") boolean enabled) implements Toggleable {
    }
}
