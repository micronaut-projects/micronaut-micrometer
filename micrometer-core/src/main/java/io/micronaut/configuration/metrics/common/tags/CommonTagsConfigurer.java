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
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_COMMON_TAGS;

/**
 * CommonTagsConfigurer will configure every MeterRegistry with common tags.
 */
@Factory
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_COMMON_TAGS)
public class CommonTagsConfigurer implements MeterRegistryConfigurer<MeterRegistry> {

    private final List<Tag> commonTags = new ArrayList<>();

    public CommonTagsConfigurer(ExportConfigurationProperties configuration) {
        Properties tags = configuration.getTags();
        tags.stringPropertyNames().forEach(key -> commonTags.add(Tag.of(key, tags.getProperty(key))));
    }

    @Override
    public void configure(MeterRegistry meterRegistry) {
        meterRegistry.config().commonTags(commonTags);
    }

    @Override
    public Class<MeterRegistry> getType() {
        return MeterRegistry.class;
    }
}
