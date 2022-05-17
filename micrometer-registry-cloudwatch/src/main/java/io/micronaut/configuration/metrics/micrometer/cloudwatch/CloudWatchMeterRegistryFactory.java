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
package io.micronaut.configuration.metrics.micrometer.cloudwatch;

import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClientBuilder;

import java.util.Properties;

import static io.micrometer.core.instrument.Clock.SYSTEM;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a CloudWatch meter registry.
 */
@Factory
public class CloudWatchMeterRegistryFactory {

    public static final String CLOUDWATCH_CONFIG = MICRONAUT_METRICS_EXPORT + ".cloudwatch";
    public static final String CLOUDWATCH_ENABLED = CLOUDWATCH_CONFIG + ".enabled";
    public static final String CLOUDWATCH_DEFAULT_NAMESPACE = "micronaut";

    /**
     * @return the CloudWatch async client builder
     */
    @Singleton
    CloudWatchAsyncClientBuilder cloudWatchAsyncClientBuilder() {
        return CloudWatchAsyncClient.builder();
    }

    /**
     * @param builder The builder to use
     * @return The CloudWatch async client.
     */
    @Bean(preDestroy = "close")
    @Singleton
    CloudWatchAsyncClient cloudWatchAsyncClient(CloudWatchAsyncClientBuilder builder) {
        return builder.build();
    }

    /**
     * Create a CloudWatchMeterRegistry bean if global metrics are enabled
     * and CloudWatch is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param exportConfigurationProperties The export configuration
     * @param cloudWatchAsyncClient  The CloudWatch async client
     * @return A CloudWatchMeterRegistry
     */
    @Singleton
    CloudWatchMeterRegistry cloudWatchMeterRegistry(
            ExportConfigurationProperties exportConfigurationProperties,
            CloudWatchAsyncClient cloudWatchAsyncClient) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        String cloudwatchNamespace = "cloudwatch.namespace";
        if (!exportConfig.containsKey(cloudwatchNamespace)) {
            exportConfig.setProperty(cloudwatchNamespace, CLOUDWATCH_DEFAULT_NAMESPACE);
        }

        return new CloudWatchMeterRegistry(
                exportConfig::getProperty,
                SYSTEM,
                cloudWatchAsyncClient
        );
    }
}
