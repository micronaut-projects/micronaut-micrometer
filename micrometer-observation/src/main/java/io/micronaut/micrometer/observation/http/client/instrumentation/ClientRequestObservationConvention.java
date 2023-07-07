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
package io.micronaut.micrometer.observation.http.client.instrumentation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * Interface for an {@link ObservationConvention} for {@link ClientHttpObservationDocumentation#HTTP_CLIENT_EXCHANGES client HTTP exchanges}.
 */
public interface ClientRequestObservationConvention extends ObservationConvention<ClientRequestObservationContext> {

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof ClientRequestObservationContext;
    }

}
