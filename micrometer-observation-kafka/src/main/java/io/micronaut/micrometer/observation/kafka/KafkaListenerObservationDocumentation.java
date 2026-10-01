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
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;
import io.micronaut.core.annotation.Internal;

/**
 * Documented {@link io.micrometer.common.KeyValue KeyValues} for the Kafka listener observations.
 * <p>This class is used by automated tools to document KeyValues attached to the Kafka listener observations.
 */
@Internal
public enum KafkaListenerObservationDocumentation implements ObservationDocumentation {

    /**
     * Kafka listener processing observations, recorded once per consumed record over its whole
     * processing lifecycle (including application retries) until its terminal outcome.
     */
    KAFKA_LISTENER {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultKafkaListenerObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    };

    /**
     * Observations low cardinality key names for Kafka listeners.
     */
    public enum LowCardinalityKeyNames implements KeyName {

        /**
         * The messaging system, always {@code "kafka"}.
         */
        MESSAGING_SYSTEM {
            @Override
            public String asString() {
                return "messaging.system";
            }
        },

        /**
         * The messaging operation, always {@code "receive"}.
         */
        MESSAGING_OPERATION {
            @Override
            public String asString() {
                return "messaging.operation";
            }
        },

        /**
         * The topic the record was consumed from.
         */
        MESSAGING_DESTINATION_NAME {
            @Override
            public String asString() {
                return "messaging.destination.name";
            }
        },

        /**
         * The Kafka consumer group id, or {@code "unknown"} when the consumer has no group.
         */
        MESSAGING_KAFKA_CONSUMER_GROUP {
            @Override
            public String asString() {
                return "messaging.kafka.consumer.group";
            }
        },

        /**
         * Simple name of the terminal exception, or {@value KeyValue#NONE_VALUE} if processing succeeded.
         */
        ERROR {
            @Override
            public String asString() {
                return "error";
            }
        }
    }

    /**
     * Observations high cardinality key names for Kafka listeners.
     */
    public enum HighCardinalityKeyNames implements KeyName {

        /**
         * The Micronaut Kafka client id of the consumer.
         */
        MESSAGING_KAFKA_CLIENT_ID {
            @Override
            public String asString() {
                return "messaging.kafka.client.id";
            }
        },

        /**
         * The partition the record was consumed from.
         */
        MESSAGING_KAFKA_DESTINATION_PARTITION {
            @Override
            public String asString() {
                return "messaging.kafka.destination.partition";
            }
        },

        /**
         * The offset of the consumed record.
         */
        MESSAGING_KAFKA_MESSAGE_OFFSET {
            @Override
            public String asString() {
                return "messaging.kafka.message.offset";
            }
        }
    }
}
