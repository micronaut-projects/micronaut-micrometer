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
package io.micronaut.micrometer.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enables or disables observations by name prefix, see {@code micrometer.observations.enable}.
 * The prefixes are matched on the segments of the observation name, separated by dots: for the
 * observation {@code http.server.requests}, the prefixes {@code http.server.requests},
 * {@code http.server} and {@code http} are looked up in that order, then {@code all}. An
 * observation without a matching prefix is enabled. The decision is cached by observation name.
 *
 * @since 6.2.0
 */
@Internal
final class PropertiesObservationPredicate implements ObservationPredicate {

    private static final String ALL = "all";

    private final Map<String, Boolean> enabled;
    private final Map<String, Boolean> decisions = new ConcurrentHashMap<>();

    /**
     * @param enabled Whether the observations are enabled, by name prefix
     */
    PropertiesObservationPredicate(Map<String, Boolean> enabled) {
        this.enabled = new HashMap<>(enabled);
    }

    @Override
    public boolean test(@Nullable String name, Observation.Context context) {
        if (name == null) {
            return isEnabled(ALL);
        }
        Boolean decision = decisions.get(name);
        if (decision == null) {
            decision = isEnabled(name);
            decisions.put(name, decision);
        }
        return decision;
    }

    private boolean isEnabled(String name) {
        String prefix = name;
        while (true) {
            Boolean value = enabled.get(prefix);
            if (value != null) {
                return value;
            }
            int lastDot = prefix.lastIndexOf('.');
            if (lastDot <= 0) {
                break;
            }
            prefix = prefix.substring(0, lastDot);
        }
        Boolean all = enabled.get(ALL);
        return all == null || all;
    }
}
