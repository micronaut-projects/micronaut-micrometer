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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import static io.micronaut.micrometer.observation.kafka.KafkaListenerObservationDocumentation.HighCardinalityKeyNames;
import static io.micronaut.micrometer.observation.kafka.KafkaListenerObservationDocumentation.LowCardinalityKeyNames;

/**
 * Default {@link KafkaListenerObservationConvention}.
 */
@Internal
public final class DefaultKafkaListenerObservationConvention implements KafkaListenerObservationConvention {

    private static final String DEFAULT_NAME = "messaging.kafka.listener";

    private static final String KAFKA_SYSTEM = "kafka";

    private static final String RECEIVE_OPERATION = "receive";

    private static final String CONSUMER_GROUP_UNKNOWN = "unknown";

    private static final KeyValue MESSAGING_SYSTEM =
        KeyValue.of(LowCardinalityKeyNames.MESSAGING_SYSTEM, KAFKA_SYSTEM);

    private static final KeyValue MESSAGING_OPERATION =
        KeyValue.of(LowCardinalityKeyNames.MESSAGING_OPERATION, RECEIVE_OPERATION);

    private static final KeyValue ERROR_NONE =
        KeyValue.of(LowCardinalityKeyNames.ERROR, KeyValue.NONE_VALUE);

    private final String name;

    /**
     * Create a convention with the default name {@code "messaging.kafka.listener"}.
     */
    public DefaultKafkaListenerObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * Create a convention with a custom name.
     * @param name the observation name
     */
    public DefaultKafkaListenerObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getContextualName(KafkaListenerObservationContext context) {
        return context.getCarrier().topic() + " " + RECEIVE_OPERATION;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(KafkaListenerObservationContext context) {
        return KeyValues.of(MESSAGING_SYSTEM, MESSAGING_OPERATION, destination(context), consumerGroup(context), error(context));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(KafkaListenerObservationContext context) {
        ConsumerRecord<?, ?> record = context.getCarrier();
        return KeyValues.of(
            KeyValue.of(HighCardinalityKeyNames.MESSAGING_KAFKA_CLIENT_ID, context.getClientId()),
            KeyValue.of(HighCardinalityKeyNames.MESSAGING_KAFKA_DESTINATION_PARTITION, Integer.toString(record.partition())),
            KeyValue.of(HighCardinalityKeyNames.MESSAGING_KAFKA_MESSAGE_OFFSET, Long.toString(record.offset()))
        );
    }

    private KeyValue destination(KafkaListenerObservationContext context) {
        return KeyValue.of(LowCardinalityKeyNames.MESSAGING_DESTINATION_NAME, context.getCarrier().topic());
    }

    private KeyValue consumerGroup(KafkaListenerObservationContext context) {
        String groupId = context.getGroupId();
        return KeyValue.of(LowCardinalityKeyNames.MESSAGING_KAFKA_CONSUMER_GROUP,
            StringUtils.hasText(groupId) ? groupId : CONSUMER_GROUP_UNKNOWN);
    }

    private KeyValue error(KafkaListenerObservationContext context) {
        Throwable error = context.getError();
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(LowCardinalityKeyNames.ERROR,
                StringUtils.hasText(simpleName) ? simpleName : error.getClass().getName());
        }
        return ERROR_NONE;
    }
}
