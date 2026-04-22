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
package io.micronaut.configuration.metrics.micrometer.stackdriver;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.stackdriver.StackdriverConfig;
import io.micrometer.stackdriver.StackdriverMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.ExportConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micrometer.core.instrument.Clock.SYSTEM;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Creates a Stackdriver meter registry.
 *
 * @author thiagolocatelli
 * @since 1.2.0
 */
@Factory
public class StackdriverMeterRegistryFactory {

    public static final String STACKDRIVER_CONFIG = MICRONAUT_METRICS_EXPORT + ".stackdriver";
    public static final String STACKDRIVER_ENABLED = STACKDRIVER_CONFIG + ".enabled";
    private static final String GOOGLE_CLOUD_PROJECT = "GOOGLE_CLOUD_PROJECT";
    private static final String GCLOUD_PROJECT = "GCLOUD_PROJECT";
    private static final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";
    private static final String CLOUDSDK_CONFIG = "CLOUDSDK_CONFIG";
    private static final String APPDATA = "APPDATA";
    private static final String APP_ENGINE_APPLICATION_ID = "com.google.appengine.application.id";
    private static final String DEFAULT_GCLOUD_CONFIG = "default";
    private static final String METADATA_FLAVOR = "Metadata-Flavor";
    private static final String GOOGLE = "Google";
    private static final int METADATA_TIMEOUT_MILLIS = 1000;
    private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("\"project_id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern GCLOUD_PROJECT_PATTERN = Pattern.compile("^project\\s*=\\s*(.*)$");
    private static final Pattern GCLOUD_SECTION_PATTERN = Pattern.compile("^\\[(.*)]$");

    /**
     * Create a StackdriverMeterRegistry bean if global metrics are enabled
     * and Stackdriver is enabled. Will be true by default when this
     * configuration is included in project.
     *
     * @param exportConfigurationProperties The export configuration
     * @return StackdriverMeterRegistry
     */
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, notEquals = FALSE)
    @Requires(property = STACKDRIVER_ENABLED, notEquals = FALSE)
    @Requires(beans = CompositeMeterRegistry.class)
    StackdriverMeterRegistry stackdriverMeterRegistry(ExportConfigurationProperties exportConfigurationProperties) {
        Properties exportConfig = exportConfigurationProperties.getExport();
        return new StackdriverMeterRegistry(stackdriverConfig(exportConfig), SYSTEM);
    }

    private StackdriverConfig stackdriverConfig(Properties exportConfig) {
        return new StackdriverConfig() {
            @Override
            public String get(String key) {
                return exportConfig.getProperty(key);
            }

            @Override
            public String projectId() {
                String configuredProjectId = exportConfig.getProperty(prefix() + ".projectId");
                return configuredProjectId != null ? configuredProjectId : getDefaultProjectId();
            }
        };
    }

    private String getDefaultProjectId() {
        String projectId = getPropertyOrEnvironment(GOOGLE_CLOUD_PROJECT);
        if (projectId == null) {
            projectId = getPropertyOrEnvironment(GCLOUD_PROJECT);
        }
        if (projectId == null) {
            projectId = getAppEngineProjectId();
        }
        if (projectId == null) {
            projectId = getServiceAccountProjectId();
        }
        return projectId != null ? projectId : getGoogleCloudProjectId();
    }

    private String getPropertyOrEnvironment(String name) {
        return System.getProperty(name, System.getenv(name));
    }

    private String getAppEngineProjectId() {
        String projectId = System.getProperty(APP_ENGINE_APPLICATION_ID);
        if (projectId == null) {
            return null;
        }
        int colonIndex = projectId.indexOf(':');
        return colonIndex > -1 ? projectId.substring(colonIndex + 1) : projectId;
    }

    private String getServiceAccountProjectId() {
        String credentialsPath = getPropertyOrEnvironment(GOOGLE_APPLICATION_CREDENTIALS);
        if (credentialsPath == null) {
            return null;
        }
        try {
            String credentialsJson = Files.readString(Path.of(credentialsPath), StandardCharsets.UTF_8);
            Matcher matcher = PROJECT_ID_PATTERN.matcher(credentialsJson);
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private String getGoogleCloudProjectId() {
        Path configDirectory = getGoogleCloudConfigDirectory();
        String activeConfig = readFirstLine(configDirectory.resolve("active_config"));
        if (activeConfig == null || activeConfig.isBlank()) {
            activeConfig = DEFAULT_GCLOUD_CONFIG;
        }
        String projectId = getGoogleCloudProjectId(configDirectory.resolve("configurations").resolve("config_" + activeConfig));
        if (projectId == null) {
            projectId = getGoogleCloudProjectId(configDirectory.resolve("properties"));
        }
        return projectId != null ? projectId : getMetadataProjectId();
    }

    private Path getGoogleCloudConfigDirectory() {
        String cloudSdkConfig = System.getenv(CLOUDSDK_CONFIG);
        if (cloudSdkConfig != null) {
            return Path.of(cloudSdkConfig);
        }
        if (isWindows()) {
            String appData = System.getenv(APPDATA);
            if (appData != null) {
                return Path.of(appData, "gcloud");
            }
        }
        return Path.of(System.getProperty("user.home"), ".config", "gcloud");
    }

    private String getGoogleCloudProjectId(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String section = null;
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(";")) {
                    continue;
                }
                Matcher sectionMatcher = GCLOUD_SECTION_PATTERN.matcher(trimmed);
                if (sectionMatcher.matches()) {
                    section = sectionMatcher.group(1);
                    continue;
                }
                Matcher projectMatcher = GCLOUD_PROJECT_PATTERN.matcher(trimmed);
                if (projectMatcher.matches() && (section == null || "core".equals(section))) {
                    return projectMatcher.group(1);
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private String readFirstLine(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream().findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String getMetadataProjectId() {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create("http://metadata.google.internal/computeMetadata/v1/project/project-id").toURL().openConnection();
            connection.setConnectTimeout(METADATA_TIMEOUT_MILLIS);
            connection.setReadTimeout(METADATA_TIMEOUT_MILLIS);
            connection.setRequestProperty(METADATA_FLAVOR, GOOGLE);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK || !GOOGLE.equals(connection.getHeaderField(METADATA_FLAVOR))) {
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                String projectId = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
                return projectId.isEmpty() ? null : projectId;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
    }
}
