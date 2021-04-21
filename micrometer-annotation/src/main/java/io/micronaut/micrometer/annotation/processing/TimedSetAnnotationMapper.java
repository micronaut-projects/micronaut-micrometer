/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.micrometer.annotation.processing;

import io.micrometer.core.annotation.Timed;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

/**
 * Adds interceptor binding to {@link Timed}.
 *
 * @since 3.4.0
 * @author graemerocher
 */
public class TimedSetAnnotationMapper implements TypedAnnotationMapper<Timed> {
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Timed> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(InterceptorBinding.class)
                        .value(Timed.class)
                        .member("kind", InterceptorKind.AROUND)
                    .build()
        );
    }

    @Override
    public Class<Timed> annotationType() {
        return Timed.class;
    }
}
