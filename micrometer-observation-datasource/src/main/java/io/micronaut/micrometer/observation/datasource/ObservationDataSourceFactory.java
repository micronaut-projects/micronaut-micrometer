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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import net.ttddyy.observation.tracing.ConnectionTracingObservationHandler;
import net.ttddyy.observation.tracing.DataSourceBaseObservationHandler;
import net.ttddyy.observation.tracing.QueryTracingObservationHandler;
import net.ttddyy.observation.tracing.ResultSetTracingObservationHandler;

/**
 * A factory class responsible for creating instances of {@link DataSourceBaseObservationHandler} based on the
 * configuration provided by {@link ObservationDataSourceConfig}.
 * This factory is used to create observation handlers that can be used to instrument database operations.
 *
 * @see ObservationDataSourceConfig
 * @see DataSourceBaseObservationHandler
 */
@Factory
@Requires(bean = Tracer.class)
@Requires(bean = ObservationDataSourceConfig.class, beanProperty = "enabled", value = "true")
@Internal
public final class ObservationDataSourceFactory {

    @Singleton
    QueryTracingObservationHandler queryTracingObservationHandler(Tracer tracer) {
        return new QueryTracingObservationHandler(tracer);
    }

    @Singleton
    ConnectionTracingObservationHandler connectionTracingObservationHandler(Tracer tracer) {
        return new ConnectionTracingObservationHandler(tracer);
    }

    @Singleton
    ResultSetTracingObservationHandler resultSetTracingObservationHandler(Tracer tracer) {
        return new ResultSetTracingObservationHandler(tracer);
    }
}
