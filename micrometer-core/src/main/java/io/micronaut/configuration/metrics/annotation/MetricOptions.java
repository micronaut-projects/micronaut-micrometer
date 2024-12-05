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
package io.micronaut.configuration.metrics.annotation;

import io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger;
import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Holds metadata about metric options to apply.
 *
 * @author Haiden Rothwell
 * @since 5.6.0
 */
@Documented
@Experimental
@Retention(RUNTIME)
@Target({METHOD})
public @interface MetricOptions {

    /**
     * Constant value to relate to annotation's tagger values.
     */
    String MEMBER_TAGGERS = "taggers";

    /**
     * Constant value to relate to annotation's filterTaggers value.
     */
    String MEMBER_FILTER_TAGGERS = "filterTaggers";

    /**
     * Constant value to relate to annotation's condition value.
     */
    String MEMBER_CONDITION = "condition";

    /**
     * @return array of {@link io.micronaut.configuration.metrics.aggregator.AbstractMethodTagger} to apply to metrics for method.
     * Only utilized for filtering if {@link #filterTaggers()} is true
     */
    Class<? extends AbstractMethodTagger>[] taggers() default {};

    /**
     * @return whether to filter taggers using {@link #taggers()} array
     */
    boolean filterTaggers() default false;

    /**
     * Evaluated expression that can be used to indicate whether the metric should be processed.
     * Will be evaluated each time the method is called, and if the condition evaluates to false the metric will not be published.
     * Evaluated using {@link io.micronaut.configuration.metrics.util.MetricOptionsUtil}
     *
     * @see <a href="https://docs.micronaut.io/latest/guide/#evaluatedExpressions">Evaluated Expressions</a>.
     * @return The condition
     * @since 5.10.0
     */
    String condition() default "";
}
