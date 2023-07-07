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

import io.micrometer.common.KeyValue;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documented {@link io.micrometer.common.KeyValue KeyValues} for the HTTP client observations.
 */
public enum ClientHttpObservationDocumentation implements ObservationDocumentation {

    /**
     * HTTP client request observations.
     */
    HTTP_CLIENT_EXCHANGES {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultClientRequestObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[] {HighCardinalityKeyNames.HTTP_URL};
        }

    };

    /**
     * Observations low cardinality key names for HTTP client.
     */
    public enum LowCardinalityKeyNames implements KeyName {

        /**
         * Name of HTTP request method.
         */
        METHOD {
            @Override
            public String asString() {
                return "method";
            }

        },

        /**
         * URI template used for HTTP request, or {@value KeyValue#NONE_VALUE} if none was provided.
         * Only the path part of the URI is considered.
         */
        URI {
            @Override
            public String asString() {
                return "uri";
            }
        },

        /**
         * HTTP response raw status code.
         * or {@code "CLIENT_ERROR"} if no response was received.
         */
        STATUS {
            @Override
            public String asString() {
                return "status";
            }
        },


        /**
         * Client name derived from the request URI host.
         */
        CLIENT_NAME {
            @Override
            public String asString() {
                return "client.name";
            }
        },

        /**
         * Name of the exception thrown during the exchange, or {@value KeyValue#NONE_VALUE} if no exception happened.
         */
        EXCEPTION {
            @Override
            public String asString() {
                return "exception";
            }
        }
    }

    /**
     * Observations high cardinality key names for HTTP client.
     */
    public enum HighCardinalityKeyNames implements KeyName {

        /**
         * HTTP request URI.
         */
        HTTP_URL {
            @Override
            public String asString() {
                return "http.url";
            }
        }
    }

}
