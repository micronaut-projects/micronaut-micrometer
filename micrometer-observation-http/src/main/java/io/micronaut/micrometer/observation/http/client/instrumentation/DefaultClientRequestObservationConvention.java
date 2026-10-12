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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;

import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

/**
 * Default implementation for a {@link ClientRequestObservationConvention} extracting information from the {@link ClientRequestObservationContext}.
 */
@Internal
public final class DefaultClientRequestObservationConvention implements ClientRequestObservationConvention {

    private static final String DEFAULT_NAME = "http.client.requests";

    private static final String HOST_HEADER = "host";

    private static final KeyValue URI_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.URI, KeyValue.NONE_VALUE);

    private static final KeyValue METHOD_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, KeyValue.NONE_VALUE);

    private static final KeyValue STATUS_CLIENT_ERROR = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "CLIENT_ERROR");

    private static final KeyValue CLIENT_NAME_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME, KeyValue.NONE_VALUE);

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    private static final KeyValue HTTP_URL_NONE = KeyValue.of(ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, KeyValue.NONE_VALUE);

    private static final int MIN_CACHED_STATUS = 100;

    // the key values and contextual names that would otherwise be created for each request
    private static final KeyValue[] STATUS_KEY_VALUES = new KeyValue[500];

    private static final KeyValue[] METHOD_KEY_VALUES = new KeyValue[HttpMethod.values().length];

    private static final String[] CONTEXTUAL_NAMES = new String[HttpMethod.values().length];

    static {
        for (int i = 0; i < STATUS_KEY_VALUES.length; i++) {
            STATUS_KEY_VALUES[i] = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, String.valueOf(MIN_CACHED_STATUS + i));
        }
        for (HttpMethod method : HttpMethod.values()) {
            METHOD_KEY_VALUES[method.ordinal()] = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, method.name());
            CONTEXTUAL_NAMES[method.ordinal()] = "http " + method.name().toLowerCase();
        }
    }

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
        return CONTEXTUAL_NAMES[context.getCarrier().getMethod().ordinal()];
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
            return METHOD_KEY_VALUES[context.getCarrier().getMethod().ordinal()];
        } else {
            return METHOD_NONE;
        }
    }

    private KeyValue status(ClientRequestObservationContext context) {
        HttpResponse<?> response = context.getResponse();
        if (response == null) {
            return STATUS_CLIENT_ERROR;
        }

        int code = response.code();
        int index = code - MIN_CACHED_STATUS;
        if (index >= 0 && index < STATUS_KEY_VALUES.length) {
            return STATUS_KEY_VALUES[index];
        }
        return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, String.valueOf(code));
    }

    private KeyValue clientName(ClientRequestObservationContext context) {
        if (context.getCarrier() != null) {
            String serviceId = context.getCarrier().getAttribute(SERVICE_ID, String.class)
                .filter(x -> !x.contains("/"))
                .orElseGet(() -> context.getCarrier().getHeaders().get(HOST_HEADER, String.class).orElse(context.getCarrier().getUri().getHost()));
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
