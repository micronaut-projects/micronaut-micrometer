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
package io.micronaut.configuration.metrics.aggregator;

import io.micrometer.core.instrument.Tag;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Appends additional {@link io.micrometer.core.instrument.Tag} to metrics annotated with {@link io.micrometer.core.annotation.Timed} and {@link io.micrometer.core.annotation.Counted}.
 *
 * @author Haiden Rothwell
 * @since 5.5.0
 */
@Indexed(AbstractMethodTagger.class)
public abstract class AbstractMethodTagger {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMethodTagger.class);

    private final Class<? extends AbstractMethodTagger> implClass = this.getClass();

    /**
     * Build list of tags using {@link io.micronaut.aop.MethodInvocationContext} which will be included on published metric.
     * @param context Context of the method annotated
     * @return List of {@link io.micrometer.core.instrument.Tag} which will be included in the metric
     */
    @NonNull protected abstract List<Tag> buildTags(@NonNull MethodInvocationContext<Object, Object> context);

    @NonNull public final List<Tag> getTags(@NonNull MethodInvocationContext<Object, Object> context) {
        List<Tag> tags = buildTags(context);
        if (tags != null) {
            return tags;
        } else {
            LOGGER.error("MethodTagger {} returned null list of tags and will not include additional tags on metric", implClass);
            return Collections.emptyList();
        }
    }
}
