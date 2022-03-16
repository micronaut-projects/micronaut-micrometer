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
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.Properties;

/**
 * Stores metrics export configuration.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@ConfigurationProperties("micronaut.metrics")
public class ExportConfigurationProperties {

    private Properties export = new Properties();

    private Properties tags = new Properties();

    /**
     * @return The export properties
     */
    public Properties getExport() {
        return export;
    }

    /**
     * @param export The export properties
     */
    public void setExport(@MapFormat(
            keyFormat = StringConvention.CAMEL_CASE,
            transformation = MapFormat.MapTransformation.FLAT) Properties export) {
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
}
