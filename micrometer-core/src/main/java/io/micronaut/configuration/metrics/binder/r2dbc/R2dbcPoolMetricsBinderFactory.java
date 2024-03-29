/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.configuration.metrics.binder.r2dbc;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolMetrics;
import io.r2dbc.spi.ConnectionFactory;

import java.util.Collections;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instruments Micronaut related R2DBC pool metrics via Micrometer.
 *
 * @author Leonardo Schick
 * @author Caroline Medeiros
 * @since 4.2.1
 */
@Factory
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".r2dbc.enabled", notEquals = FALSE)
public class R2dbcPoolMetricsBinderFactory {

    /**
     * Wires beans for each DataSource.
     *
     * @param dataSourceName    The parameterized name of the datasource
     * @param factory           The datasource factory object to use for the binder
     * @return                  MeterBinders for each configured {@link ConnectionFactory}
     */
    @EachBean(ConnectionFactory.class)
    @Requires(beans = {ConnectionFactory.class})
    public MeterBinder r2dbcPoolMeterBinder(@Parameter String dataSourceName,
                                            ConnectionFactory factory) {
        PoolMetrics poolMetrics = null;
        if (factory instanceof ConnectionPool) {
            poolMetrics = ((ConnectionPool) factory).getMetrics().orElse(null);
        }

        return new R2dbcPoolMetricsBinder(poolMetrics, dataSourceName, Collections.emptyList());
    }
}
