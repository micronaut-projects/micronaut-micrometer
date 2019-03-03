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
package io.micronaut.configuration.metrics.micrometer.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder;
import io.micrometer.cloudwatch.CloudWatchConfig;
import io.micrometer.cloudwatch.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The CloudWatchMeterRegistryFactory that will configure and create a cloudwatch meter registry.
 */
@Factory
public class CloudWatchMeterRegistryFactory {
    public static final String CLOUDWATCH_CONFIG = MICRONAUT_METRICS_EXPORT + ".cloudwatch";
    public static final String CLOUDWATCH_ENABLED = CLOUDWATCH_CONFIG + ".enabled";
    public static final String CLOUDWATCH_NAMESPACE = CLOUDWATCH_CONFIG + ".namespace";

    private final CloudWatchConfig cloudWatchConfig;

    /**
     * Sets the underlying cloudwatch meter registry properties.
     *
     * @param cloudwatchConfigurationProperties cloudwatch properties
     */
    CloudWatchMeterRegistryFactory(final CloudWatchConfigurationProperties cloudwatchConfigurationProperties) {
        this.cloudWatchConfig = cloudwatchConfigurationProperties;
    }

    /**
     * Create a CloudWatchMeterRegistry bean if global metrics are enables
     * and the cloudwatch is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A CloudWatchMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = CLOUDWATCH_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    CloudWatchMeterRegistry cloudWatchMeterRegistry() {
        return new CloudWatchMeterRegistry(cloudWatchConfig, Clock.SYSTEM, AmazonCloudWatchAsyncClientBuilder.defaultClient());
    }
}
