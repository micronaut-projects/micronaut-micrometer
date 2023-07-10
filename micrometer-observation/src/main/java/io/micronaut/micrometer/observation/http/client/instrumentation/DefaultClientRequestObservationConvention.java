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
import io.micrometer.common.KeyValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;

import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

/**
 * Default implementation for a {@link ClientRequestObservationConvention} extracting information from the {@link ClientRequestObservationContext}.
 */
public final class DefaultClientRequestObservationConvention implements ClientRequestObservationConvention {

    private static final String DEFAULT_NAME = "http.client.requests";

    private static final KeyValue URI_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.URI, KeyValue.NONE_VALUE);

    private static final KeyValue METHOD_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, KeyValue.NONE_VALUE);

    private static final KeyValue STATUS_CLIENT_ERROR = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "CLIENT_ERROR");

    private static final KeyValue CLIENT_NAME_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME, KeyValue.NONE_VALUE);

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    private static final KeyValue HTTP_URL_NONE = KeyValue.of(ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, KeyValue.NONE_VALUE);

    private final String name;

    /**
     * Create a convention with the default name {@code "http.client.requests"}.
     */
    public DefaultClientRequestObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * Create a convention with a custom name.
     * @param name the observation name
     */
    public DefaultClientRequestObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(ClientRequestObservationContext context) {
        return "http " + context.getCarrier().getMethod().name().toLowerCase();
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ClientRequestObservationContext context) {
        return KeyValues.of(clientName(context), exception(context), method(context), status(context), uri(context));
    }

    private KeyValue uri(ClientRequestObservationContext context) {
        if (context.getCarrier() != null) {
            String uri = (String) context.getCarrier().getAttribute(URI_TEMPLATE).orElse(KeyValue.NONE_VALUE);
            return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.URI, uri);
        }
        return URI_NONE;
    }

    private KeyValue method(ClientRequestObservationContext context) {
        if (context.getCarrier() != null) {
            return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, context.getCarrier().getMethod().name());
        } else {
            return METHOD_NONE;
        }
    }

    private KeyValue status(ClientRequestObservationContext context) {
        HttpResponse<?> response = context.getResponse();
        if (response == null) {
            return STATUS_CLIENT_ERROR;
        }

        return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, String.valueOf(response.getStatus().getCode()));
    }

    private KeyValue clientName(ClientRequestObservationContext context) {
        if (context.getCarrier() != null) {
            String serviceId = context.getCarrier().getAttribute(SERVICE_ID, String.class)
                .filter(x -> !x.contains("/"))
                .orElseGet(() -> context.getCarrier().getRemoteAddress().getHostString());
            return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME, serviceId);
        }
        return CLIENT_NAME_NONE;
    }

    private KeyValue exception(ClientRequestObservationContext context) {
        Throwable error = context.getError();
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION,
                StringUtils.hasText(simpleName) ? simpleName : error.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ClientRequestObservationContext context) {
        return KeyValues.of(requestUri(context));
    }

    private KeyValue requestUri(ClientRequestObservationContext context) {
        if (context.getCarrier() != null) {
            return KeyValue.of(ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, context.getCarrier().getUri().toASCIIString());
        }
        return HTTP_URL_NONE;
    }

}
