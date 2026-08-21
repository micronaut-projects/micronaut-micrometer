/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_COMMON_TAGS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_TAGS;

/**
 * Activates common tag configuration when either supported property prefix is configured.
 */
public final class CommonTagsCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        return containsConfiguredTags(context, MICRONAUT_METRICS_TAGS)
            || containsConfiguredTags(context, MICRONAUT_METRICS_COMMON_TAGS);
    }

    private static boolean containsConfiguredTags(ConditionContext<?> context, String property) {
        return context.containsProperty(property) || context.containsProperties(property);
    }
}
