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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

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
    private boolean enabled;
    private boolean traceConnection;
    private boolean traceQuery;
    private boolean traceResultSet;

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
     * Returns whether connection tracing is enabled.
     *
     * @return true if connection tracing is enabled, false otherwise
     */
    public boolean isTraceConnection() {
        return traceConnection;
    }

    /**
     * Enables or disables connection tracing.
     *
     * @param traceConnection true to enable connection tracing, false otherwise
     */
    public void setTraceConnection(boolean traceConnection) {
        this.traceConnection = traceConnection;
    }

    /**
     * Returns whether query tracing is enabled.
     *
     * @return true if query tracing is enabled, false otherwise
     */
    public boolean isTraceQuery() {
        return traceQuery;
    }

    /**
     * Enables or disables query tracing.
     *
     * @param traceQuery true to enable query tracing, false otherwise
     */
    public void setTraceQuery(boolean traceQuery) {
        this.traceQuery = traceQuery;
    }

    /**
     * Returns whether result set tracing is enabled.
     *
     * @return true if result set tracing is enabled, false otherwise
     */
    public boolean isTraceResultSet() {
        return traceResultSet;
    }

    /**
     * Enables or disables result set tracing.
     *
     * @param traceResultSet true to enable result set tracing, false otherwise
     */
    public void setTraceResultSet(boolean traceResultSet) {
        this.traceResultSet = traceResultSet;
    }
}
