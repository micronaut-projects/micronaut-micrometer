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
package io.micronaut.micrometer.test;

import io.micronaut.configuration.metrics.micrometer.MetricsExportCondition;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;

import java.util.List;
import java.util.Map;

/**
 * Disables the meter registries that export metrics in the {@code test} environment, so that
 * tests do not publish metrics to monitoring systems. The meters are then recorded in a
 * {@code SimpleMeterRegistry} that tests can inspect.
 *
 * <p>The configurer contributes {@code micronaut.metrics.export.defaults.enabled=false} with a
 * lower precedence than the configuration files of the application, so a registry is still
 * enabled when its {@code micronaut.metrics.export.<name>.enabled} property is {@code true}, and
 * {@code micronaut.metrics.export.defaults.enabled=true} restores the default.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@ContextConfigurer
public final class MetricsTestContextConfigurer implements ApplicationContextConfigurer {

    /**
     * The name of the property source.
     */
    public static final String PROPERTY_SOURCE_NAME = "micronaut-micrometer-test";

    /**
     * The position of the property source, lower than the configuration files.
     */
    public static final int POSITION = AbstractPropertySourceLoader.DEFAULT_POSITION - 1000;

    @Override
    public void configure(ApplicationContextBuilder builder) {
        builder.propertySourcesLocator(environment -> {
            if (!environment.getActiveNames().contains(Environment.TEST)) {
                return List.of();
            }
            return List.of(PropertySource.of(PROPERTY_SOURCE_NAME,
                Map.of(MetricsExportCondition.DEFAULTS_ENABLED, false), POSITION));
        });
    }
}
