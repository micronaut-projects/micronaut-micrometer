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
package io.micronaut.configuration.metrics.binder.system;

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;

import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Records the time at which an embedded application, such as the HTTP server, has started, which
 * {@link StartupTimeMetrics} reports as {@code application.ready.time}.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Singleton
@RequiresMetrics
@Requires(classes = ApplicationStartupEvent.class)
@Requires(property = StartupTimeMetrics.ENABLED, notEquals = FALSE)
public class ApplicationReadyTimeMetrics implements ApplicationEventListener<ApplicationStartupEvent> {

    private final StartupTimeMetrics startupTimeMetrics;

    /**
     * @param startupTimeMetrics The startup time metrics
     */
    public ApplicationReadyTimeMetrics(StartupTimeMetrics startupTimeMetrics) {
        this.startupTimeMetrics = startupTimeMetrics;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        startupTimeMetrics.ready(StartupTimeMetrics.uptimeMillis());
    }
}
