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
package io.micronaut.micrometer.observation.utils;

import io.micrometer.observation.Observation;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.micrometer.observation.ObservationPropagationContext;
import reactor.util.context.ContextView;

/**
 * Utils class to get the Observation from the Reactor context.
 */
@Experimental
public final class ObservedReactorPropagation {

    private ObservedReactorPropagation() {
    }

    /**
     * Find the Observation from the reactive context.
     * @param contextView The reactor's context view
     * @return The found observation.
     */
    public static Observation currentObservation(ContextView contextView) {
        return ReactorPropagation.findContextElement(contextView, ObservationPropagationContext.class)
            .map(ObservationPropagationContext::observation).orElse(null);
    }

}
