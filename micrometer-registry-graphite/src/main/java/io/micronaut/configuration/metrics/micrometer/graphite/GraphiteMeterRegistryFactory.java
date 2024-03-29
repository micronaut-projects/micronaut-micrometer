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
package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Properties;

import static io.micrometer.core.instrument.Clock.SYSTEM;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;
import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Creates a Graphite meter registry.
 */
@Factory
public class GraphiteMeterRegistryFactory {

    public static final String GRAPHITE_CONFIG = MICRONAUT_METRICS_EXPORT + ".graphite";
    public static final String GRAPHITE_TAGS_AS_PREFIX = GRAPHITE_CONFIG + ".tags-as-prefix";
    public static final String GRAPHITE_ENABLED = GRAPHITE_CONFIG + ".enabled";

    /**
     * Create a GraphiteMeterRegistry bean if global metrics are enabled
     * and Graphite is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param config the Graphite config
     * @return GraphiteMeterRegistry
     */
    @Singleton
    GraphiteMeterRegistry graphiteMeterRegistry(GraphiteConfig config) {
        return new GraphiteMeterRegistry(config, SYSTEM);
    }

    /**
     * Create a GraphiteMeterRegistry bean if global metrics are enabled
     * and Graphite is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param exportConfigurationProperties The export configuration
     * @param tagsAsPrefix                  The tags as prefix
     * @return GraphiteMeterRegistry
     */
    @Singleton
    GraphiteConfig graphiteConfig(ExportConfigurationProperties exportConfigurationProperties,
                                  @Property(name = GRAPHITE_TAGS_AS_PREFIX) @Nullable List<String> tagsAsPrefix) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new GraphiteConfig() {
            @Override
            public String get(String key) {
                return exportConfig.getProperty(key);
            }

            @Override
            public String[] tagsAsPrefix() {
                return tagsAsPrefix != null ? tagsAsPrefix.toArray(EMPTY_STRING_ARRAY) : EMPTY_STRING_ARRAY;
            }
        };
    }
}
