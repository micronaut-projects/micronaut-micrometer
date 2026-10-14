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
package io.micronaut.configuration.metrics.binder.logging;

import io.micrometer.core.instrument.binder.logging.Log4j2Metrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import java.util.Collections;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Creates the Log4j 2 metrics bean when Log4j Core is the Log4j API implementation.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Factory
@RequiresMetrics
@Requires(classes = LoggerContext.class)
@Requires(property = Log4j2MeterRegistryBinderFactory.ENABLED, notEquals = FALSE)
@Requires(condition = Log4j2CoreLoggerContextCondition.class)
public class Log4j2MeterRegistryBinderFactory {

    /**
     * Whether Log4j 2 metrics are enabled.
     */
    public static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".log4j2.enabled";

    /**
     * Log4j 2 metrics bean ({@code log4j2.events}). It adds a filter to the Log4j configuration
     * that is removed when the application shuts down.
     *
     * @return log4j2Metrics bean
     */
    @Singleton
    @Bean(preDestroy = "close")
    public Log4j2Metrics log4j2Metrics() {
        return new Log4j2Metrics(Collections.emptyList(), (LoggerContext) LogManager.getContext(false));
    }
}
