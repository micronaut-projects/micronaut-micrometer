/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.Primary;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Singleton;
import net.ttddyy.dsproxy.support.ProxyDataSource;

import javax.sql.DataSource;
import java.util.List;

/**
 * A custom implementation of {@link DataSourceResolver} that resolves the underlying target data source
 * even when it has been wrapped by proxying or instrumentation logic.
 * This resolver first delegates to another {@link DataSourceResolver} instance and then checks if the
 * resolved data source is an instance of {@link ProxyDataSource}. If so, it returns the actual data
 * source wrapped by the proxy.
 *
 * @author radovanradic
 * @since 5.1.0
 */
@Singleton
@Primary
public class ProxyDataSourceResolver implements DataSourceResolver {

    private final List<DataSourceResolver> dataSourceResolvers;

    ProxyDataSourceResolver(List<DataSourceResolver> dataSourceResolvers) {
        this.dataSourceResolvers = dataSourceResolvers;
    }

    @Override
    public DataSource resolve(DataSource dataSource) {
        // The list of resolvers can be empty
        for (DataSourceResolver dataSourceResolver : dataSourceResolvers) {
            dataSource = dataSourceResolver.resolve(dataSource);
        }
        // If there was no resolver, make sure to unwrap DelegatingDataSource
        dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
        // And finally ProxyDataSource
        if (dataSource instanceof ProxyDataSource proxyDataSource) {
            dataSource = proxyDataSource.getDataSource();
        }
        return dataSource;
    }
}
