/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.configuration.metrics.util;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.annotation.MetricOptions;
import io.micronaut.core.expressions.EvaluatedExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for {@link MetricOptions}. Sharing means of evaluating condition
 *
 * @since 5.10.0
 * @author Haiden Rothwell
 */
final public class MetricOptionsUtil {

    private static final Logger LOG = LoggerFactory.getLogger(MetricOptionsUtil.class);

    /**
     * Evaluates the condition ({@link EvaluatedExpression}) contained within the {@link MethodInvocationContext}'s {@link MetricOptions} annotation.
     * If no condition is present, the default value of true is returned.
     *
     * @param context {@link MethodInvocationContext} to evaluate for
     * @return condition's result
     */
    public static boolean evaluateCondition(MethodInvocationContext<?, ?> context) {
        if (!context.isPresent(MetricOptions.class, MetricOptions.MEMBER_CONDITION)) {
            return true;
        }
        boolean expressionResult = context.booleanValue(MetricOptions.class, MetricOptions.MEMBER_CONDITION).orElse(false);
        if (LOG.isDebugEnabled()) {
            LOG.debug("MetricOptions condition evaluated to {} for invocation: {}", expressionResult, context);
        }
        return expressionResult;
    }
}
