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

import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;
import io.micronaut.core.annotation.Internal;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Context that holds information for metadata collection regarding
 * {@link KafkaListenerObservationDocumentation#KAFKA_LISTENER Kafka listener} observations.
 *
 * <p>This context extends {@link ReceiverContext} with {@link Kind#CONSUMER} so that any incoming
 * trace context carried on the consumed record's headers can be joined during processing.</p>
 */
@Internal
public final class KafkaListenerObservationContext extends ReceiverContext<ConsumerRecord<?, ?>> {

    private final String clientId;

    @Nullable
    private final String groupId;

    /**
     * @param record   the record being processed
     * @param clientId the Micronaut Kafka client id of the consumer
     * @param groupId  the Kafka consumer group id, if any
     */
    public KafkaListenerObservationContext(ConsumerRecord<?, ?> record, String clientId, @Nullable String groupId) {
        super(KafkaListenerObservationContext::header, Kind.CONSUMER);
        setCarrier(record);
        this.clientId = clientId;
        this.groupId = groupId;
    }

    /**
     * @return the Micronaut Kafka client id of the consumer
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @return the Kafka consumer group id, or {@code null} if the consumer has no group
     */
    @Nullable
    public String getGroupId() {
        return groupId;
    }

    @Nullable
    private static String header(@Nullable ConsumerRecord<?, ?> record, String name) {
        if (record == null) {
            return null;
        }
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
