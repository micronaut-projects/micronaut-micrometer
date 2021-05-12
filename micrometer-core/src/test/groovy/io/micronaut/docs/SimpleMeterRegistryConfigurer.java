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
package io.micronaut.docs;

import io.micronaut.core.annotation.Order;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.order.Ordered;

import javax.inject.Singleton;

@Order(Integer.MAX_VALUE)
@Singleton
@RequiresMetrics
public class SimpleMeterRegistryConfigurer implements MeterRegistryConfigurer<SimpleMeterRegistry>, Ordered {

    @Override
    public void configure(SimpleMeterRegistry meterRegistry) {
        meterRegistry.config().commonTags("key", "value");
    }

    @Override
    public Class<SimpleMeterRegistry> getType() {
        return SimpleMeterRegistry.class;
    }
}
