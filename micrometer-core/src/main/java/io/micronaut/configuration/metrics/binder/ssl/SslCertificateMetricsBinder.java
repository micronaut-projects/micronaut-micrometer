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
package io.micronaut.configuration.metrics.binder.ssl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.ssl.CertificateProvider;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

/**
 * Reports the expiry of the certificates of every {@link CertificateProvider} bean (configured
 * under {@code micronaut.certificate.*}) as the {@code ssl.chain.expiry} gauge, in seconds until
 * the certificate that expires first in each chain expires. The value is negative once the
 * certificate has expired.
 *
 * <p>Each gauge has the tags {@code provider} (the certificate provider name), {@code store}
 * ({@code key-store} or {@code trust-store}), {@code chain} (the key store alias) and
 * {@code certificate} (the serial number of the certificate that expires first, in hexadecimal).</p>
 *
 * <p>A provider is reported once it has been created, for example because the HTTP server or an
 * HTTP client uses it, so the binder never creates providers itself. The binder subscribes to the
 * key store and trust store publishers of each provider, so reloaded certificates are picked up.
 * Only the expiry dates are kept: reading a gauge does not read a key store.</p>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
@Singleton
@RequiresMetrics
@Requires(classes = CertificateProvider.class)
@Requires(beans = CertificateProvider.class)
@Requires(property = SslCertificateMetricsBinder.ENABLED, notEquals = FALSE)
@Bean(preDestroy = "close")
public class SslCertificateMetricsBinder implements MeterBinder, BeanCreatedEventListener<CertificateProvider>, AutoCloseable {

    /**
     * Whether SSL certificate metrics are enabled.
     */
    public static final String ENABLED = MICRONAUT_METRICS_BINDERS + ".ssl.enabled";

    /**
     * The gauge name.
     */
    public static final String SSL_CHAIN_EXPIRY = "ssl.chain.expiry";

    private static final Logger LOG = LoggerFactory.getLogger(SslCertificateMetricsBinder.class);
    private static final String KEY_STORE = "key-store";
    private static final String TRUST_STORE = "trust-store";

    private final Map<String, List<ChainExpiry>> expiries = new LinkedHashMap<>();
    private final Map<String, StoreExpiries> stores = new HashMap<>();
    private final List<RegistryGauge> gauges = new ArrayList<>(1);
    private final List<Disposable> subscriptions = new ArrayList<>();

    @Override
    public CertificateProvider onCreated(BeanCreatedEvent<CertificateProvider> event) {
        CertificateProvider provider = event.getBean();
        Publisher<KeyStore> keyStore = provider.getKeyStore();
        Publisher<KeyStore> trustStore = provider.getTrustStore();
        subscribe(provider.getName(), KEY_STORE, keyStore);
        // by default the trust store is the key store. A provider can still return a new publisher of
        // the same key store, which update recognizes by the store it emits
        if (trustStore != keyStore) {
            subscribe(provider.getName(), TRUST_STORE, trustStore);
        }
        return provider;
    }

    @Override
    public synchronized void bindTo(MeterRegistry registry) {
        MultiGauge gauge = MultiGauge.builder(SSL_CHAIN_EXPIRY)
            .description("SSL certificate chain expiry")
            .baseUnit("seconds")
            .register(registry);
        RegistryGauge registryGauge = new RegistryGauge(registry, gauge);
        gauges.add(registryGauge);
        registryGauge.update(expiries);
    }

    @Override
    public synchronized void close() {
        subscriptions.forEach(Disposable::dispose);
        subscriptions.clear();
    }

    private void subscribe(String provider, String store, Publisher<KeyStore> publisher) {
        Disposable subscription = Flux.from(publisher).subscribe(
            keyStore -> update(provider, store, keyStore, readExpiries(provider, store, keyStore)),
            error -> LOG.warn("Failed to read the {} of certificate provider '{}' for SSL certificate metrics", store, provider, error)
        );
        synchronized (this) {
            subscriptions.add(subscription);
        }
    }

    private synchronized void update(String provider, String store, KeyStore keyStore, List<ChainExpiry> chains) {
        stores.put(key(provider, store), new StoreExpiries(keyStore, chains));
        StoreExpiries keyStoreExpiries = stores.get(key(provider, KEY_STORE));
        StoreExpiries trustStoreExpiries = stores.get(key(provider, TRUST_STORE));
        if (keyStoreExpiries != null) {
            expiries.put(key(provider, KEY_STORE), keyStoreExpiries.chains());
        }
        // a trust store that is the key store is reported once, as the key store
        if (trustStoreExpiries == null
            || keyStoreExpiries != null && trustStoreExpiries.keyStore() == keyStoreExpiries.keyStore()) {
            expiries.remove(key(provider, TRUST_STORE));
        } else {
            expiries.put(key(provider, TRUST_STORE), trustStoreExpiries.chains());
        }
        for (RegistryGauge gauge : gauges) {
            gauge.update(expiries);
        }
    }

    private static String key(String provider, String store) {
        return provider + '\u0000' + store;
    }

    private static List<ChainExpiry> readExpiries(String provider, String store, KeyStore keyStore) {
        List<ChainExpiry> result = new ArrayList<>();
        try {
            for (String alias : Collections.list(keyStore.aliases())) {
                Certificate[] chain = keyStore.isKeyEntry(alias)
                    ? keyStore.getCertificateChain(alias)
                    : new Certificate[] {keyStore.getCertificate(alias)};
                if (chain == null) {
                    continue;
                }
                Arrays.stream(chain)
                    .filter(X509Certificate.class::isInstance)
                    .map(X509Certificate.class::cast)
                    .min(Comparator.comparing(X509Certificate::getNotAfter))
                    .ifPresent(certificate -> result.add(new ChainExpiry(
                        Tags.of("provider", provider, "store", store, "chain", alias, "certificate", certificate.getSerialNumber().toString(16)),
                        certificate.getNotAfter().toInstant()
                    )));
            }
        } catch (KeyStoreException e) {
            LOG.warn("Failed to read the {} of certificate provider '{}' for SSL certificate metrics", store, provider, e);
        }
        return List.copyOf(result);
    }

    /**
     * The expiry of the certificate that expires first in a chain.
     *
     * @param tags     The gauge tags
     * @param notAfter The expiry
     */
    private record ChainExpiry(Tags tags, Instant notAfter) {
    }

    /**
     * The expiries of the latest key store a publisher of a provider emitted.
     *
     * @param keyStore The key store
     * @param chains   The expiries of its chains
     */
    private record StoreExpiries(KeyStore keyStore, List<ChainExpiry> chains) {
    }

    /**
     * The gauge registered with one registry.
     *
     * @param registry The registry, whose clock is used to compute the remaining time
     * @param gauge    The gauge
     */
    private record RegistryGauge(MeterRegistry registry, MultiGauge gauge) {

        void update(Map<String, List<ChainExpiry>> expiries) {
            List<MultiGauge.Row<?>> rows = new ArrayList<>();
            for (List<ChainExpiry> chains : expiries.values()) {
                for (ChainExpiry chain : chains) {
                    long notAfter = chain.notAfter().toEpochMilli();
                    rows.add(MultiGauge.Row.of(chain.tags(),
                        () -> TimeUnit.MILLISECONDS.toSeconds(notAfter - registry.config().clock().wallTime())));
                }
            }
            gauge.register(rows, true);
        }
    }
}
