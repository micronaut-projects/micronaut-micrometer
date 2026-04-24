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
package io.micronaut.configuration.metrics.common.tags;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_COMMON_TAGS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_TAGS;

/**
 * Configures every MeterRegistry with common tags.
 */
@Factory
@RequiresMetrics
@Requires(condition = CommonTagsCondition.class)
public class CommonTagsConfigurer implements MeterRegistryConfigurer<MeterRegistry> {

    private final List<Tag> commonTags = new ArrayList<>();

    public CommonTagsConfigurer(Environment environment) {
        Map<String, Object> tags = new TreeMap<>();
        tags.putAll(readConfiguredTags(environment, MICRONAUT_METRICS_TAGS));
        tags.putAll(readConfiguredTags(environment, MICRONAUT_METRICS_COMMON_TAGS));
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            commonTags.add(Tag.of(entry.getKey(), String.valueOf(entry.getValue())));
        }
    }

    @Override
    public void configure(MeterRegistry meterRegistry) {
        meterRegistry.config().commonTags(commonTags);
    }

    @Override
    public Class<MeterRegistry> getType() {
        return MeterRegistry.class;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readConfiguredTags(Environment environment, String property) {
        Map<String, Object> configuredTags = new LinkedHashMap<>();
        if (environment.containsProperty(property)) {
            configuredTags.putAll(environment.getProperty(property, Map.class).orElse(Map.of()));
        }
        if (environment.containsProperties(property)) {
            configuredTags.putAll(environment.getProperties(property, StringConvention.RAW));
        }
        return configuredTags;
    }
}
