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
package io.micronaut.configuration.metrics.filter;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;

/**
 * Matches when any {@code micronaut.metrics.enable.*} or {@code micronaut.metrics.distribution.*}
 * property is configured, so that {@link PropertiesMeterFilter} only exists when it has work to do.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Internal
public final class PropertiesMeterFilterCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        return context.containsProperties(MetricsFilterProperties.ENABLE_PREFIX)
            || context.containsProperties(MetricsFilterProperties.DISTRIBUTION_PREFIX);
    }
}
