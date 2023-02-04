/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;

import java.util.Properties;

import static io.micronaut.core.convert.format.MapFormat.MapTransformation.FLAT;
import static io.micronaut.core.naming.conventions.StringConvention.CAMEL_CASE;

/**
 * Stores metrics export configuration.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@ConfigurationProperties("micronaut.metrics")
public class ExportConfigurationProperties {
    /**
     * The default value for enabling 404 uris as tags
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_404_URI_ENABLED = false;
    private Properties export = new Properties();

    private Properties tags = new Properties();

    private boolean enable404uriTag = DEFAULT_404_URI_ENABLED;

    /**
     * @return The export properties
     */
    public Properties getExport() {
        return export;
    }

    /**
     * @param export The export properties
     */
    public void setExport(@MapFormat(keyFormat = CAMEL_CASE, transformation = FLAT) Properties export) {
        this.export = export;
    }

    /**
     * @return The common tags properties
     */
    public Properties getTags() {
        return tags;
    }

    /**
     * @param tags The common tags properties
     */
    public void setTags(Properties tags) {
        this.tags = tags;
    }

    /**
     * @return if 404 uri tag should be set
     */
    public boolean isEnable404uriTag() {
        return enable404uriTag;
    }

    /**
     * @param enable404uriTag set uri tag if http status is 404. Default value ({@value #DEFAULT_404_URI_ENABLED}).
     */
    public void setEnable404uriTag(boolean enable404uriTag) {
        this.enable404uriTag = enable404uriTag;
    }
}
