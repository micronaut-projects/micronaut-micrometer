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
package io.micronaut.configuration.metrics.annotation;

import io.micronaut.configuration.metrics.micrometer.MetricsExportCondition;
import io.micronaut.context.annotation.Requires;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Apply to the configuration of a meter registry that exports metrics. The registry is enabled
 * when the given {@code enabled} property is {@code true}. When the property is not set, it is
 * enabled unless {@value io.micronaut.configuration.metrics.micrometer.MetricsExportCondition#DEFAULTS_ENABLED}
 * is {@code false}.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Documented
@Retention(RUNTIME)
@Target({PACKAGE, TYPE, ANNOTATION_TYPE, METHOD})
@Requires(condition = MetricsExportCondition.class)
public @interface RequiresMetricsExport {

    /**
     * @return The property that enables the registry, for example
     * {@code micronaut.metrics.export.prometheus.enabled}
     */
    String value();
}
