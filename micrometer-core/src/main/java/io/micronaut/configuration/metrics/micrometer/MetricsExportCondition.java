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
package io.micronaut.configuration.metrics.micrometer;

import io.micronaut.configuration.metrics.annotation.RequiresMetricsExport;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;

import java.util.Optional;

/**
 * The condition of {@link RequiresMetricsExport}: the registry's own {@code enabled} property
 * wins when it is set, otherwise {@value #DEFAULTS_ENABLED} decides, which defaults to
 * {@code true}.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
public final class MetricsExportCondition implements Condition {

    /**
     * Whether meter registries that export metrics are enabled when their own {@code enabled}
     * property is not set.
     */
    public static final String DEFAULTS_ENABLED = MeterRegistryFactory.MICRONAUT_METRICS_EXPORT + ".defaults.enabled";

    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        Optional<String> property = component.getAnnotationMetadata().stringValue(RequiresMetricsExport.class);
        if (property.isPresent()) {
            Optional<Boolean> enabled = context.getProperty(property.get(), Boolean.class);
            if (enabled.isPresent()) {
                if (!enabled.get()) {
                    context.fail("Property [" + property.get() + "] is false");
                }
                return enabled.get();
            }
        }
        boolean defaultsEnabled = context.getProperty(DEFAULTS_ENABLED, Boolean.class).orElse(true);
        if (!defaultsEnabled) {
            context.fail("Property [" + DEFAULTS_ENABLED + "] is false and [" + property.orElse("enabled") + "] is not set");
        }
        return defaultsEnabled;
    }
}
