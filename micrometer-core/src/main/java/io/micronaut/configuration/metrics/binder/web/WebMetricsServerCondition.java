/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.configuration.metrics.binder.web;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.reflect.ClassUtils;

/**
 * A custom {@link Condition} that defines if {@link ServerRequestMeterRegistryFilter} should be created.
 */
public class WebMetricsServerCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        boolean isClassPresent = ClassUtils.isPresent("io.micronaut.micrometer.observation.http.server.ObservationServerFilter", context.getBeanContext().getClassLoader());

        if (!context.containsProperty("micrometer.observation.http.server.enabled") && isClassPresent) {
            return false;
        }

        return !context.containsProperty("micrometer.observation.server.server.enabled") || !context.getProperty("micrometer.observation.http.server.enabled", Boolean.class).orElse(false);
    }
}
