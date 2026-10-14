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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * Matches when the Log4j API is backed by Log4j Core. The Log4j API can also be routed to
 * another logging framework, for example with {@code log4j-to-slf4j}, while Log4j Core is on the
 * classpath, in which case there is no Log4j Core logger context to instrument.
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Internal
public final class Log4j2CoreLoggerContextCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        return LogManager.getContext(false) instanceof LoggerContext;
    }
}
