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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.observation.tracing.DataSourceObservationListener;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * A Micronaut event listener that wraps DataSources with an observation proxy when created.
 *
 * This listener checks if the observation data source is enabled via the {@link ObservationDataSourceConfig}.
 * If enabled, it creates a proxy around the original data source using the {@link ProxyDataSourceBuilder},
 * adding a {@link DataSourceObservationListener} to capture observations.
 *
 * @see ObservationDataSourceConfig
 * @see ObservationRegistry
 */
@Singleton
@Requires(beans = {ObservationDataSourceConfig.class, ObservationRegistry.class})
@Internal
final class DataSourceBeanCreatedEventListener implements BeanCreatedEventListener<DataSource>, Ordered {

    private final ObservationDataSourceConfig observationDataSourceConfig;
    @Nullable
    private final ProxyDataSourceBuilder builder;

    /**
     * Constructs a new instance of the event listener.
     *
     * @param observationDataSourceConfig The observation data source configuration
     * @param builder The nullable {@link ProxyDataSourceBuilder} which users can generate and inject.
     *                If not provided then new instance will be created with default value.
     */
    DataSourceBeanCreatedEventListener(ObservationDataSourceConfig observationDataSourceConfig,
                                       @Nullable ProxyDataSourceBuilder builder) {
        this.observationDataSourceConfig = observationDataSourceConfig;
        this.builder = builder;
    }

    /**
     * Wraps the created data source with an observation proxy if the observation data source is enabled.
     *
     * @param event the bean creation event
     * @return the wrapped data source or the original data source if observation is disabled
     */
    @Override
    public DataSource onCreated(@NonNull BeanCreatedEvent<DataSource> event) {
        DataSource dataSource = event.getBean();
        if (!observationDataSourceConfig.isEnabled()) {
            return dataSource;
        }
        String name = event.getBeanIdentifier().getName();
        DataSourceObservationListener listener = observationDataSourceConfig.getListener();
        ProxyDataSourceBuilder finalBuilder;
        finalBuilder = Objects.requireNonNullElseGet(builder, ProxyDataSourceBuilder::new);
        return finalBuilder.name(name).dataSource(dataSource).listener(listener).methodListener(listener).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
