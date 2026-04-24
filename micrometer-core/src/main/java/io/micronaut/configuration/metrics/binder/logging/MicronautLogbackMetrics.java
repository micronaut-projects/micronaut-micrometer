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
package io.micronaut.configuration.metrics.binder.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

import static java.util.Collections.emptyList;

/**
 * Async-safe variant of {@link LogbackMetrics}.
 */
final class MicronautLogbackMetrics extends LogbackMetrics {

    private final Iterable<Tag> tags;
    private final LoggerContext loggerContext;
    private final Map<MeterRegistry, MetricsTurboFilter> metricsTurboFilters = new HashMap<>();

    MicronautLogbackMetrics() {
        this(emptyList());
    }

    MicronautLogbackMetrics(Iterable<Tag> tags) {
        this(tags, (LoggerContext) LoggerFactory.getILoggerFactory());
    }

    MicronautLogbackMetrics(Iterable<Tag> tags, LoggerContext loggerContext) {
        super(tags, loggerContext);
        this.tags = tags;
        this.loggerContext = loggerContext;

        loggerContext.addListener(new LoggerContextListener() {
            @Override
            public boolean isResetResistant() {
                return true;
            }

            @Override
            public void onReset(LoggerContext context) {
                synchronized (metricsTurboFilters) {
                    for (MetricsTurboFilter metricsTurboFilter : metricsTurboFilters.values()) {
                        loggerContext.addTurboFilter(metricsTurboFilter);
                    }
                }
            }

            @Override
            public void onStart(LoggerContext context) {
                // no-op
            }

            @Override
            public void onStop(LoggerContext context) {
                // no-op
            }

            @Override
            public void onLevelChange(Logger logger, Level level) {
                // no-op
            }
        });
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        MetricsTurboFilter filter = new MetricsTurboFilter(registry, tags);
        synchronized (metricsTurboFilters) {
            metricsTurboFilters.put(registry, filter);
            loggerContext.addTurboFilter(filter);
        }
    }

    @Override
    public void close() {
        synchronized (metricsTurboFilters) {
            for (MetricsTurboFilter metricsTurboFilter : metricsTurboFilters.values()) {
                loggerContext.getTurboFilterList().remove(metricsTurboFilter);
            }
        }
    }

    static final class MetricsTurboFilter extends TurboFilter {

        private static final String METER_NAME = "logback.events";
        private static final String METER_DESCRIPTION = "Number of log events that were enabled by the effective log level";

        private final LongAdder errorCount = new LongAdder();
        private final LongAdder warnCount = new LongAdder();
        private final LongAdder infoCount = new LongAdder();
        private final LongAdder debugCount = new LongAdder();
        private final LongAdder traceCount = new LongAdder();

        MetricsTurboFilter(MeterRegistry registry, Iterable<Tag> tags) {
            FunctionCounter.builder(METER_NAME, errorCount, LongAdder::doubleValue)
                .tags(tags)
                .tags("level", "error")
                .description(METER_DESCRIPTION)
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            FunctionCounter.builder(METER_NAME, warnCount, LongAdder::doubleValue)
                .tags(tags)
                .tags("level", "warn")
                .description(METER_DESCRIPTION)
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            FunctionCounter.builder(METER_NAME, infoCount, LongAdder::doubleValue)
                .tags(tags)
                .tags("level", "info")
                .description(METER_DESCRIPTION)
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            FunctionCounter.builder(METER_NAME, debugCount, LongAdder::doubleValue)
                .tags(tags)
                .tags("level", "debug")
                .description(METER_DESCRIPTION)
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            FunctionCounter.builder(METER_NAME, traceCount, LongAdder::doubleValue)
                .tags(tags)
                .tags("level", "trace")
                .description(METER_DESCRIPTION)
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);
        }

        @Override
        public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
            if (format == null || !level.isGreaterOrEqual(logger.getEffectiveLevel())) {
                return FilterReply.NEUTRAL;
            }

            recordMetrics(level);
            return FilterReply.NEUTRAL;
        }

        private void recordMetrics(Level level) {
            switch (level.toInt()) {
                case Level.ERROR_INT:
                    errorCount.increment();
                    break;
                case Level.WARN_INT:
                    warnCount.increment();
                    break;
                case Level.INFO_INT:
                    infoCount.increment();
                    break;
                case Level.DEBUG_INT:
                    debugCount.increment();
                    break;
                case Level.TRACE_INT:
                    traceCount.increment();
                    break;
                default:
                    break;
            }
        }
    }
}
