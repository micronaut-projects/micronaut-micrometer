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
package io.micronaut.configuration.metrics.filter;

import io.micrometer.core.instrument.Meter;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A configured meter value: a plain number, or a duration such as {@code 100ms}.
 *
 * <p>Values are parsed once when the filter is created. {@link #getValue(Meter.Type)} converts
 * them to the unit Micrometer expects for the meter type: nanoseconds for timers and the raw
 * number for distribution summaries. A plain number used for a timer is read as milliseconds.</p>
 *
 * @param number   The value when it was configured as a number
 * @param duration The value when it was configured as a duration
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Internal
record MeterValue(@Nullable Double number, @Nullable Duration duration) {

    /**
     * Parse a configured value.
     *
     * @param property          The property the value comes from, used in error messages
     * @param value             The configured value
     * @param conversionService The conversion service used to parse durations
     * @return The meter value
     */
    static MeterValue parse(String property, String value, ConversionService conversionService) {
        String trimmed = value.trim();
        try {
            return new MeterValue(Double.parseDouble(trimmed), null);
        } catch (NumberFormatException e) {
            Duration duration = conversionService.convert(trimmed, Duration.class).orElseThrow(() ->
                new ConfigurationException("Invalid value '" + value + "' for '" + property + "'. Expected a number or a duration such as 100ms"));
            return new MeterValue(null, duration);
        }
    }

    /**
     * @param type The meter type
     * @return The value in the unit Micrometer expects for the meter type, or {@code null} if the
     * value does not apply to the meter type
     */
    @Nullable
    Double getValue(Meter.Type type) {
        return switch (type) {
            case TIMER, LONG_TASK_TIMER -> {
                if (duration != null) {
                    yield (double) duration.toNanos();
                }
                yield number == null ? null : (double) TimeUnit.MILLISECONDS.toNanos(number.longValue());
            }
            case DISTRIBUTION_SUMMARY -> number;
            default -> null;
        };
    }
}
