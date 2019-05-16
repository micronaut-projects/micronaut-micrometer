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

package io.micronaut.configuration.metrics.micrometer.kairos;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.kairos.KairosConfig;
import io.micrometer.kairos.KairosMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The KairosMeterRegistryFactory that will configure and create a kairos meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class KairosMeterRegistryFactory {

    public static final String KAIROS_CONFIG = MICRONAUT_METRICS_EXPORT + ".kairos";
    public static final String KAIROS_ENABLED = KAIROS_CONFIG + ".enabled";

    private final KairosConfig kairosConfig;

    /**
     * Sets the underlying new kairos meter registry properties.
     *
     * @param kairosConfigurationProperties kairos properties
     */
    public KairosMeterRegistryFactory(KairosConfigurationProperties kairosConfigurationProperties) {
        this.kairosConfig = kairosConfigurationProperties;
    }

    /**
     * Create a KairosMeterRegistry bean if global metrics are enables
     * and the kairos is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A KairosMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = KAIROS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    KairosMeterRegistry kairosMeterRegistry() {
        return new KairosMeterRegistry(kairosConfig, Clock.SYSTEM);
    }

}
