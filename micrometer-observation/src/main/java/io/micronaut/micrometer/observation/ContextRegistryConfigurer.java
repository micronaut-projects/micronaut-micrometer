/*
 * Copyright 2017-2023 original authors
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

import io.micrometer.context.ContextRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;

/**
 * Configurer removes {@link ObservationThreadLocalAccessor} which is not needed with Micronaut integration.
 */
@Prototype
@Context
@Requires(classes = ContextRegistry.class)
public final class ContextRegistryConfigurer {

    @PostConstruct
    void config() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationThreadLocalAccessor.getInstance().key().toString());
    }

}
