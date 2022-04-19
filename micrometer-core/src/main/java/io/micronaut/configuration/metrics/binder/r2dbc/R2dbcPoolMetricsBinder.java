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
package io.micronaut.configuration.metrics.binder.r2dbc;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import javax.validation.constraints.NotNull;

import io.r2dbc.pool.PoolMetrics;

import java.util.function.Function;

/**
 * A {@link MeterBinder} for a {@link ConnectionPool}.
 *
 * @author Leonardo Schick
 * @author Caroline Medeiros
 * @since 4.2.1
 */
public class R2dbcPoolMetricsBinder implements MeterBinder {

    private final PoolMetrics poolMetrics;
    private final Iterable<Tag> tags;

    public R2dbcPoolMetricsBinder(PoolMetrics metrics, String dataSourceName, Iterable<Tag> tags) {
        this.poolMetrics = metrics;
        this.tags = Tags.concat(tags, "name", dataSourceName);
    }

    @Override
    public void bindTo(@NotNull MeterRegistry registry) {
        if (poolMetrics != null) {
            bindToPoolMetrics(registry, "acquired", PoolMetrics::acquiredSize);
            bindToPoolMetrics(registry, "allocated", PoolMetrics::allocatedSize);
            bindToPoolMetrics(registry, "idle", PoolMetrics::idleSize);
            bindToPoolMetrics(registry, "pending", PoolMetrics::pendingAcquireSize);
            bindToPoolMetrics(registry, "max.allocated", PoolMetrics::getMaxAllocatedSize);
            bindToPoolMetrics(registry, "max.pending", PoolMetrics::getMaxPendingAcquireSize);
        }
    }

    private void bindToPoolMetrics(
        MeterRegistry registry,
        String metricName,
        Function<PoolMetrics, Integer> function
    ) {
        registry.gauge(
            "r2dbc.pool" + "." + metricName,
            tags,
            poolMetrics,
            (m) -> function.apply(m).doubleValue()
        );
    }
}
