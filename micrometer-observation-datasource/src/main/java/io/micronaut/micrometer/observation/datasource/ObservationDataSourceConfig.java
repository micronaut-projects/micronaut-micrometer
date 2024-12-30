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
package io.micronaut.micrometer.observation.datasource;

import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import net.ttddyy.observation.tracing.DataSourceObservationListener;

/**
 * Configuration properties for the observation data source.
 *
 * This configuration allows you to control the behavior of the observation data source,
 * including enabling/disabling it and tracing connections, queries, and result sets.
 *
 * @author radovanradic
 * @since 5.11
 */
@Requires(property = ObservationDataSourceConfig.PREFIX + ".enabled", value = StringUtils.TRUE)
@ConfigurationProperties(ObservationDataSourceConfig.PREFIX)
public class ObservationDataSourceConfig implements Toggleable {

    static final String PREFIX = "micrometer.observation.datasource";
    @ConfigurationBuilder(value = "listener", prefixes = "set")
    final DataSourceObservationListener listener;

    private boolean enabled;

    ObservationDataSourceConfig(ObservationRegistry observationRegistry) {
        listener = new DataSourceObservationListener(observationRegistry);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the observation data source is enabled or disabled.
     *
     * @param enabled true to enable the observation data source, false otherwise
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the {@link DataSourceObservationListener} instance associated with this configuration.
     *
     * This listener is used to observe and record events related to the data source.
     *
     * @return the data source observation listener
     */
    public DataSourceObservationListener getListener() {
        return listener;
    }
}
