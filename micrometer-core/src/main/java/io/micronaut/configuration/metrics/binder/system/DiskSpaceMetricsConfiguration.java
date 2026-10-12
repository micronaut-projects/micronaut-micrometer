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
package io.micronaut.configuration.metrics.binder.system;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;

import java.util.List;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Configuration of the disk space metrics.
 *
 * @param enabled Whether disk space metrics are enabled. Defaults to {@code true}.
 * @param paths   The paths whose file system is reported. Defaults to the working directory.
 * @author Graeme Rocher
 * @since 6.2.0
 */
@ConfigurationProperties(DiskSpaceMetricsConfiguration.PREFIX)
public record DiskSpaceMetricsConfiguration(
    @Bindable(defaultValue = "true") boolean enabled,
    @Bindable(defaultValue = ".") List<String> paths
) implements Toggleable {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = MICRONAUT_METRICS_BINDERS + ".disk-space";

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
