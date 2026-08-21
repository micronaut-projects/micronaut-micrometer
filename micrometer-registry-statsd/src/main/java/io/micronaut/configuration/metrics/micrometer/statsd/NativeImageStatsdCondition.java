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
package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

/**
 * Enables the native-image StatsD workaround only for UDP-based native executables.
 */
final class NativeImageStatsdCondition implements Condition {

    static final String NATIVE_IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";
    private static final String RUNTIME = "runtime";
    private static final String UDP = "UDP";

    @Override
    public boolean matches(ConditionContext context) {
        String imageCode = System.getProperty(NATIVE_IMAGE_CODE_PROPERTY);
        if (!RUNTIME.equalsIgnoreCase(imageCode)) {
            return false;
        }
        String protocol = context.getProperty(StatsdMeterRegistryFactory.STATSD_CONFIG + ".protocol", String.class)
            .orElse(UDP);
        return UDP.equalsIgnoreCase(protocol);
    }
}
