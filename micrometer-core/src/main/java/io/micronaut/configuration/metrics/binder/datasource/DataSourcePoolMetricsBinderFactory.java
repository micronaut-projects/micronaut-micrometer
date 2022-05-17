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
package io.micronaut.configuration.metrics.binder.datasource;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata;

import java.util.Collections;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Instruments Micronaut related JDBC pool metrics via Micrometer.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".jdbc.enabled", notEquals = FALSE)
public class DataSourcePoolMetricsBinderFactory {

    /**
     * Wires beans for each DataSource.
     *
     * @param dataSourceName         The parameterized name of the datasource
     * @param dataSourcePoolMetadata The datasource metadata object to use for the binder
     * @return MeterDinders for each configured {@link DataSourcePoolMetadata}
     */
    @EachBean(DataSourcePoolMetadata.class)
    @Requires(beans = {DataSourcePoolMetadata.class})
    public MeterBinder dataSourceMeterBinder(
            @Parameter String dataSourceName,
            DataSourcePoolMetadata dataSourcePoolMetadata) {
        return new DataSourcePoolMetricsBinder(dataSourcePoolMetadata.getDataSource(),
                dataSourcePoolMetadata,
                dataSourceName,
                Collections.emptyList());
    }
}
