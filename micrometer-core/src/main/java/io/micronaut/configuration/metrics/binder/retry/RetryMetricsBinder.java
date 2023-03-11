/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.configuration.metrics.binder.retry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.retry.event.RetryEventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Instruments Micronaut retries via Micrometer.
 *
 * @author Robert Young
 */
@Singleton
@RequiresMetrics
@Requires(property = RetryMetricsBinder.RETRY_METRICS_ENABLED, notEquals = StringUtils.FALSE, defaultValue = StringUtils.FALSE)
public class RetryMetricsBinder implements RetryEventListener {

    public static final String RETRY_METRICS_ENABLED = MICRONAUT_METRICS_BINDERS + ".retry.enabled";
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryMetricsBinder.class);
    private final BeanProvider<MeterRegistry> meterRegistryProvider;
    private final HashMap<ExecutableMethod<?, ?>, Counter> attemptCounters = new HashMap<>();

    /**
     * @param meterRegistryProvider The meter registry provider
     */
    public RetryMetricsBinder(BeanProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public void onApplicationEvent(RetryEvent event) {
        if (!meterRegistryProvider.isPresent()) {
            LOGGER.debug("meter registry not present in provider, ignoring retry event");
            return;
        }
        ExecutableMethod<?, ?> executableMethod = event.getSource().getExecutableMethod();
        Counter retryCounter = attemptCounters.computeIfAbsent(executableMethod, (method) -> {
            String description = method.getDescription(true);
            MeterRegistry meterRegistry = meterRegistryProvider.get();
            String declaringTypeName = method.getDeclaringType().getName();
            return meterRegistry.counter(
                "micronaut.retry.attempt.total",
                "method_description", description,
                "declaring_type", declaringTypeName
            );
        });
        retryCounter.increment();
    }

}
