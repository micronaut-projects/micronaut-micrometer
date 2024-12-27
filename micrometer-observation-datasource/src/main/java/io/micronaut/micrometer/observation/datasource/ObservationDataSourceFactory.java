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

import io.micrometer.tracing.Tracer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import net.ttddyy.observation.tracing.ConnectionTracingObservationHandler;
import net.ttddyy.observation.tracing.DataSourceBaseObservationHandler;
import net.ttddyy.observation.tracing.QueryTracingObservationHandler;
import net.ttddyy.observation.tracing.ResultSetTracingObservationHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * A factory class responsible for creating instances of {@link DataSourceBaseObservationHandler} based on the
 * configuration provided by {@link ObservationDataSourceConfig}.
 *
 * This factory is used to create observation handlers that can be used to instrument database operations.
 *
 * @see ObservationDataSourceConfig
 * @see DataSourceBaseObservationHandler
 */
@Factory
@Requires(bean = Tracer.class)
@Internal
public final class ObservationDataSourceFactory {

    private final ObservationDataSourceConfig observationDataSourceConfig;

    /**
     * Constructs an instance of ObservationDatasourceFactory with the given configuration.
     *
     * @param observationDataSourceConfig the configuration for the observation data source
     */
    ObservationDataSourceFactory(ObservationDataSourceConfig observationDataSourceConfig) {
        this.observationDataSourceConfig = observationDataSourceConfig;
    }

    /**
     * Creates a list of {@link DataSourceBaseObservationHandler} instances based on the current configuration.
     *
     * If the observation data source is enabled, this method will create handlers for connection, query, and result set tracing
     * depending on the corresponding settings in the configuration.
     *
     * @param tracer the tracer instance used by the created handlers
     * @return a list of observation handlers
     */
    @Bean
    List<DataSourceBaseObservationHandler> dataSourceBaseObservationHandlers(@NonNull Tracer tracer) {
        List<DataSourceBaseObservationHandler> handlers = new ArrayList<>(3);
        if (observationDataSourceConfig.isEnabled()) {
            if (observationDataSourceConfig.isTraceConnection()) {
                handlers.add(new ConnectionTracingObservationHandler(tracer));
            }
            if (observationDataSourceConfig.isTraceQuery()) {
                handlers.add(new QueryTracingObservationHandler(tracer));
            }
            if (observationDataSourceConfig.isTraceResultSet()) {
                handlers.add(new ResultSetTracingObservationHandler(tracer));
            }
        }
        return handlers;
    }
}
