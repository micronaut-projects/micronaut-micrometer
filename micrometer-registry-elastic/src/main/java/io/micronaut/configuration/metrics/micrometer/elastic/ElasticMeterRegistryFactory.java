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
package io.micronaut.configuration.metrics.micrometer.elastic;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.elastic.ElasticConfig;
import io.micrometer.elastic.ElasticMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The ElasticMeterRegistryFactory that will configure and create a elastic meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class ElasticMeterRegistryFactory {

    public static final String ELASTIC_CONFIG = MICRONAUT_METRICS_EXPORT + ".elastic";
    public static final String ELASTIC_ENABLED = ELASTIC_CONFIG + ".enabled";

    private final ElasticConfig elasticConfig;

    /**
     * Sets the underlying elastic meter registry properties.
     *
     * @param elasticConfigurationProperties prometheus properties
     */
    public ElasticMeterRegistryFactory(ElasticConfigurationProperties elasticConfigurationProperties) {
        this.elasticConfig = elasticConfigurationProperties;
    }

    /**
     * Create a ElasticMeterRegistry bean if global metrics are enables
     * and the datadog is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A ElasticMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = ELASTIC_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    ElasticMeterRegistry elasticConfig() {
        return new ElasticMeterRegistry(elasticConfig, Clock.SYSTEM);
    }

}
