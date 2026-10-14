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
package io.micronaut.configuration.metrics.binder.web;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The request paths excluded from the web metrics: a path is excluded when it matches one of the
 * regular expressions entirely, as with {@link java.util.regex.Matcher#matches()}.
 *
 * @since 6.2.0
 */
@Internal
final class HttpPathExclusions implements Predicate<String> {

    private final Pattern[] patterns;

    private HttpPathExclusions(Pattern[] patterns) {
        this.patterns = patterns;
    }

    /**
     * @param exclusions The regular expressions, or {@code null}
     * @return The predicate testing whether a path is excluded, or {@code null} if nothing is excluded
     */
    @Nullable
    static Predicate<String> of(@Nullable List<String> exclusions) {
        if (exclusions == null || exclusions.isEmpty()) {
            return null;
        }
        Pattern[] patterns = new Pattern[exclusions.size()];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = Pattern.compile(exclusions.get(i));
        }
        return new HttpPathExclusions(patterns);
    }

    @Override
    public boolean test(String path) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }
}
