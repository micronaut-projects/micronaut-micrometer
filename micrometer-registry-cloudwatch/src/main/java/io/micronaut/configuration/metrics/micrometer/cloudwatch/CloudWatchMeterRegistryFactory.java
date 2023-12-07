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
import io.micronaut.aws.sdk.v2.service.AWSServiceConfiguration;
import io.micronaut.aws.sdk.v2.service.AwsClientFactory;
import io.micronaut.aws.ua.UserAgentProvider;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClientBuilder;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClientBuilder;

import java.util.Properties;

import static io.micrometer.core.instrument.Clock.SYSTEM;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * Creates a CloudWatch meter registry.
 */
@Factory
public class CloudWatchMeterRegistryFactory extends AwsClientFactory<CloudWatchClientBuilder, CloudWatchAsyncClientBuilder, CloudWatchClient, CloudWatchAsyncClient> {

    public static final String CLOUDWATCH_CONFIG = MICRONAUT_METRICS_EXPORT + ".cloudwatch";
    public static final String CLOUDWATCH_ENABLED = CLOUDWATCH_CONFIG + ".enabled";
    public static final String CLOUDWATCH_DEFAULT_NAMESPACE = "micronaut";

    protected CloudWatchMeterRegistryFactory(AwsCredentialsProviderChain credentialsProvider,
                                          AwsRegionProviderChain regionProvider,
                                          @Nullable UserAgentProvider userAgentProvider,
                                          @Nullable @Named(CloudWatchClient.SERVICE_NAME) AWSServiceConfiguration awsServiceConfiguration) {
        super(credentialsProvider, regionProvider, userAgentProvider, awsServiceConfiguration);
    }

    @Override
    protected CloudWatchClientBuilder createSyncBuilder() {
        return CloudWatchClient.builder();
    }

    @Override
    protected CloudWatchAsyncClientBuilder createAsyncBuilder() {
        return CloudWatchAsyncClient.builder();
    }

    @Override
    @Singleton
    public CloudWatchClientBuilder syncBuilder(SdkHttpClient httpClient) {
        return super.syncBuilder(httpClient);
    }

    @Override
    @Bean(preDestroy = "close")
    @Singleton
    public CloudWatchClient syncClient(CloudWatchClientBuilder builder) {
        return super.syncClient(builder);
    }

    @Override
    @Singleton
    @Requires(beans = SdkAsyncHttpClient.class)
    public CloudWatchAsyncClientBuilder asyncBuilder(SdkAsyncHttpClient httpClient) {
        return super.asyncBuilder(httpClient);
    }

    @Override
    @Bean(preDestroy = "close")
    @Singleton
    @Requires(beans = SdkAsyncHttpClient.class)
    public CloudWatchAsyncClient asyncClient(CloudWatchAsyncClientBuilder builder) {
        return super.asyncClient(builder);
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
    CloudWatchMeterRegistry cloudWatchMeterRegistry(ExportConfigurationProperties exportConfigurationProperties,
                                                    CloudWatchAsyncClient cloudWatchAsyncClient) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        String cloudwatchNamespace = "cloudwatch.namespace";
        if (!exportConfig.containsKey(cloudwatchNamespace)) {
            exportConfig.setProperty(cloudwatchNamespace, CLOUDWATCH_DEFAULT_NAMESPACE);
        }

        return new CloudWatchMeterRegistry(exportConfig::getProperty, SYSTEM, cloudWatchAsyncClient);
    }
}
