package io.micronaut.configuration.metrics.binder.ssl

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ssl.CertificateProvider
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.pkitesting.CertificateBuilder
import io.netty.pkitesting.X509Bundle
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Sinks
import spock.lang.Specification
import spock.lang.TempDir

import java.security.KeyStore
import java.time.Duration
import java.time.Instant

class SslCertificateMetricsBinderSpec extends Specification {

    @TempDir
    File tempDir

    void "the binder does not exist without certificate providers"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        !ctx.containsBean(SslCertificateMetricsBinder)

        cleanup:
        ctx.close()
    }

    void "the expiry of configured certificates is reported"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.certificate.self-signed.my-cert.lifetime': '10d'
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)
        ctx.getBean(CertificateProvider, Qualifiers.byName('my-cert'))

        when:
        Gauge gauge = registry.get('ssl.chain.expiry').tag('provider', 'my-cert').tag('store', 'key-store').gauge()

        then:
        ctx.containsBean(SslCertificateMetricsBinder)
        gauge.id.baseUnit == 'seconds'
        gauge.id.getTag('chain')
        gauge.id.getTag('certificate') ==~ /[0-9a-f]+/
        Math.abs(gauge.value() - Duration.ofDays(10).toSeconds()) < 120
        // the trust store of the provider is its key store, so it is not reported twice
        registry.find('ssl.chain.expiry').tag('store', 'trust-store').gauges().empty

        cleanup:
        ctx.close()
    }

    void "the default trust store of a resource certificate provider is not reported twice"() {
        given: 'a provider whose key store and trust store publishers are new publishers of the same store'
        X509Bundle certificate = bundle('CN=resource', Duration.ofDays(15))
        File file = new File(tempDir, 'resource.p12')
        file.withOutputStream { certificate.toKeyStore('secret'.toCharArray()).store(it, 'secret'.toCharArray()) }
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.certificate.resource.res-cert.resource': file.toURI().toString(),
                'micronaut.certificate.resource.res-cert.password': 'secret'
        ])
        MeterRegistry registry = ctx.getBean(MeterRegistry)
        CertificateProvider provider = ctx.getBean(CertificateProvider, Qualifiers.byName('res-cert'))

        expect:
        !provider.keyStore.is(provider.trustStore)
        List<Gauge> keyStoreGauges = registry.find('ssl.chain.expiry').tag('provider', 'res-cert').tag('store', 'key-store').gauges().toList()
        !keyStoreGauges.empty
        keyStoreGauges.every { Math.abs(it.value() - Duration.ofDays(15).toSeconds()) < 120 }
        registry.find('ssl.chain.expiry').tag('provider', 'res-cert').tag('store', 'trust-store').gauges().empty

        cleanup:
        ctx.close()
    }

    void "the certificate of the https server is reported"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.certificate.self-signed.server-cert.lifetime': '20d',
                'micronaut.server.ssl.enabled'                          : true,
                'micronaut.server.ssl.port'                             : -1,
                'micronaut.server.ssl.key-name'                         : 'server-cert'
        ])
        MeterRegistry registry = server.applicationContext.getBean(MeterRegistry)

        when:
        Gauge gauge = registry.get('ssl.chain.expiry').tag('provider', 'server-cert').gauge()

        then:
        Math.abs(gauge.value() - Duration.ofDays(20).toSeconds()) < 120

        cleanup:
        server.close()
    }

    void "reloaded key stores and separate trust stores are reported"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'SslCertificateMetricsBinderSpec'])
        TestCertificateProvider provider = ctx.getBean(TestCertificateProvider)
        MeterRegistry registry = ctx.getBean(MeterRegistry)

        expect:
        expiry(registry, 'key-store') > Duration.ofDays(4).toSeconds()
        expiry(registry, 'key-store') <= Duration.ofDays(5).toSeconds()
        expiry(registry, 'trust-store') > Duration.ofDays(29).toSeconds()

        when: 'a new key store is published'
        X509Bundle renewed = bundle('CN=renewed', Duration.ofDays(60))
        provider.keyStores.tryEmitNext(renewed.toKeyStore(null))
        List<Gauge> keyStoreGauges = registry.find('ssl.chain.expiry').tag('store', 'key-store').gauges().toList()

        then: 'the gauges of the previous certificate are removed'
        !keyStoreGauges.empty
        keyStoreGauges.every { it.id.getTag('certificate') == renewed.certificate.serialNumber.toString(16) }
        keyStoreGauges.every { it.value() > Duration.ofDays(59).toSeconds() }
        registry.find('ssl.chain.expiry').tag('store', 'trust-store').gauges().size() == 1

        when: 'an expired certificate is published'
        provider.keyStores.tryEmitNext(bundle('CN=expired', Duration.ofDays(-1)).toKeyStore(null))

        then:
        expiry(registry, 'key-store') < 0

        cleanup:
        ctx.close()
    }

    void "the binder can be disabled"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.certificate.self-signed.my-cert.lifetime': '10d',
                'micronaut.metrics.binders.ssl.enabled'             : false
        ])

        expect:
        !ctx.containsBean(SslCertificateMetricsBinder)
        !ctx.getBean(MeterRegistry).find('ssl.chain.expiry').gauge()

        cleanup:
        ctx.close()
    }

    private static double expiry(MeterRegistry registry, String store) {
        registry.get('ssl.chain.expiry').tag('provider', 'test').tag('store', store).gauge().value()
    }

    static X509Bundle bundle(String subject, Duration lifetime) {
        Instant notAfter = Instant.now().plus(lifetime)
        new CertificateBuilder()
                .subject(subject)
                .notBefore(notAfter.minus(Duration.ofDays(365)))
                .notAfter(notAfter)
                .setIsCertificateAuthority(true)
                .buildSelfSigned()
    }

    @Singleton
    @Named('test')
    @Requires(property = 'spec.name', value = 'SslCertificateMetricsBinderSpec')
    static class TestCertificateProvider implements CertificateProvider {

        final Sinks.Many<KeyStore> keyStores = Sinks.many().replay().latest()
        final Sinks.Many<KeyStore> trustStores = Sinks.many().replay().latest()

        TestCertificateProvider() {
            keyStores.tryEmitNext(bundle('CN=key', Duration.ofDays(5)).toKeyStore(null))
            KeyStore trustStore = KeyStore.getInstance('PKCS12')
            trustStore.load(null, null)
            trustStore.setCertificateEntry('trusted', bundle('CN=trusted', Duration.ofDays(30)).certificate)
            trustStores.tryEmitNext(trustStore)
        }

        @Override
        Publisher<KeyStore> getKeyStore() {
            keyStores.asFlux()
        }

        @Override
        Publisher<KeyStore> getTrustStore() {
            trustStores.asFlux()
        }

        @Override
        String getName() {
            'test'
        }
    }
}
