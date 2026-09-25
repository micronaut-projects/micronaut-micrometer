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
package io.micronaut.micrometer.observation.kafka;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.configuration.kafka.KafkaConsumerProcessingObserver;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.core.util.StringUtils.TRUE;

/**
 * A {@link KafkaConsumerProcessingObserver} that records a Micrometer {@link Observation} for the
 * whole processing lifecycle of each consumed record, from first delivery through to the record's
 * terminal outcome.
 *
 * <p>The observation is started in {@link #onStart} and stopped in exactly one of {@link #onSuccess}
 * or {@link #onError}, mirroring the way the HTTP server filter brackets a request. Because the
 * consumer processor reuses the returned handle across application retries of the same record, a
 * single observation spans all retry attempts rather than one per attempt.</p>
 */
@Internal
@Singleton
@Requires(classes = KafkaConsumerProcessingObserver.class)
@Requires(beans = ObservationRegistry.class)
@Requires(property = "micrometer.observation.kafka.enabled", notEquals = FALSE, defaultValue = TRUE)
public final class ObservationKafkaListenerProcessingObserver implements KafkaConsumerProcessingObserver {

    private static final KafkaListenerObservationConvention DEFAULT_CONVENTION = new DefaultKafkaListenerObservationConvention();

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final KafkaListenerObservationConvention observationConvention;

    /**
     * @param observationRegistry   the observation registry
     * @param observationConvention a user-supplied convention overriding the default, if any
     */
    public ObservationKafkaListenerProcessingObserver(
        ObservationRegistry observationRegistry,
        @Nullable KafkaListenerObservationConvention observationConvention
    ) {
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
    }

    @Override
    @Nullable
    public Object onStart(ConsumerRecord<?, ?> record, String clientId, @Nullable String groupId) {
        KafkaListenerObservationContext context = new KafkaListenerObservationContext(record, clientId, groupId);
        return KafkaListenerObservationDocumentation.KAFKA_LISTENER
            .observation(observationConvention, DEFAULT_CONVENTION, () -> context, observationRegistry)
            .start();
    }

    @Override
    public void onSuccess(Object handle) {
        ((Observation) handle).stop();
    }

    @Override
    public void onError(Object handle, Throwable error) {
        Observation observation = (Observation) handle;
        observation.error(error);
        observation.stop();
    }
}
