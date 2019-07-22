package io.micronaut.configuration.metrics.micrometer;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.Properties;

/**
 * Stores metrics export configuration
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@ConfigurationProperties("micronaut.metrics")
public class ExportConfigurationProperties {

    private Properties export = new Properties();

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
}
