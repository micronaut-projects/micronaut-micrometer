/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.metrics.micrometer.ganglia;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.ganglia.GangliaConfig;
import io.micrometer.ganglia.GangliaMeterRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The GangliaMeterRegistryFactory that will configure and create a ganglia meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class GangliaMeterRegistryFactory {

    public static final String GANGLIA_CONFIG = MICRONAUT_METRICS_EXPORT + ".ganglia";
    public static final String GANGLIA_ENABLED = GANGLIA_CONFIG + ".enabled";

    private final GangliaConfig gangliaConfig;

    /**
     * Sets the underlying Ganglia meter registry properties.
     *
     * @param gangliaConfigurationProperties ganglia properties
     */
    public GangliaMeterRegistryFactory(GangliaConfigurationProperties gangliaConfigurationProperties) {
        this.gangliaConfig = gangliaConfigurationProperties;
    }

    /**
     * Create a GangliaMeterRegistry bean if global metrics are enables
     * and the ganglia is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A GangliaMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = GANGLIA_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    GangliaMeterRegistry gangliaMeterRegistry() {
        return new GangliaMeterRegistry(gangliaConfig, Clock.SYSTEM);
    }

}
