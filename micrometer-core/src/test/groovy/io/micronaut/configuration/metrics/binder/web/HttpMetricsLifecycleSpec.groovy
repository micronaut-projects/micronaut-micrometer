package io.micronaut.configuration.metrics.binder.web

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micronaut.configuration.metrics.binder.web.config.HttpClientMeterConfig
import io.micronaut.configuration.metrics.binder.web.config.HttpServerMeterConfig
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * When the HTTP metrics are recorded: once per request, after the response was written.
 */
class HttpMetricsLifecycleSpec extends Specification {

    static final String SPEC_NAME = 'HttpMetricsLifecycleSpec'

    PollingConditions conditions = new PollingConditions(timeout: 5)

    @AutoCleanup
    EmbeddedServer embeddedServer

    @AutoCleanup
    HttpClient client

    MeterRegistry registry

    private void start(Map<String, Object> properties = [:]) {
        embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME] + properties)
        registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
    }

    private List<Timer> serverTimers(String uri) {
        registry.find(HttpServerMeterConfig.REQUESTS_METRIC).tag('uri', uri).timers() as List<Timer>
    }

    private List<Timer> clientTimers() {
        registry.find(HttpClientMeterConfig.REQUESTS_METRIC).timers() as List<Timer>
    }

    void "a response whose body cannot be written is recorded once, with the replacing 500 response (#1233)"() {
        given:
        start()

        when:
        client.toBlocking().exchange('/lifecycle/writer-error', String)

        then:
        HttpClientResponseException e = thrown()
        e.code() == 500
        conditions.eventually {
            List<Timer> timers = serverTimers('/lifecycle/writer-error')
            assert timers.size() == 1
            assert timers[0].id.getTag('status') == '500'
            assert timers[0].id.getTag('exception') == 'IllegalStateException'
            assert timers[0].count() == 1
        }
    }

    @PendingFeature(reason = 'Core 5.3 answers a CodecException of a body writer with the default error response, without running the response filters again')
    void "a response whose body writer fails with a CodecException is recorded with the replacing 500 response"() {
        given:
        start()

        when:
        client.toBlocking().exchange('/lifecycle/codec-error', String)

        then:
        HttpClientResponseException e = thrown()
        e.code() == 500
        conditions.within(1) {
            List<Timer> timers = serverTimers('/lifecycle/codec-error')
            assert timers.size() == 1
            assert timers[0].id.getTag('status') == '500'
        }
    }

    void "a streamed response is recorded once it completed"() {
        given:
        start()
        long start = System.nanoTime()

        when:
        String body = client.toBlocking().retrieve('/lifecycle/stream')
        long elapsed = System.nanoTime() - start

        then:
        body == 'chunk-1\nchunk-2\nchunk-3\n'
        conditions.eventually {
            List<Timer> timers = serverTimers('/lifecycle/stream')
            assert timers.size() == 1
            assert timers[0].count() == 1
            assert timers[0].totalTime(TimeUnit.NANOSECONDS) >= Duration.ofMillis(250).toNanos()
            assert timers[0].totalTime(TimeUnit.NANOSECONDS) <= elapsed
        }
    }

    void "a streamed response cancelled by the client is recorded once"() {
        given:
        start()
        StreamingHttpClient streamingClient = embeddedServer.applicationContext.createBean(StreamingHttpClient, embeddedServer.URL)

        when: 'the client takes the first chunk of an endless stream and cancels'
        def first = Flux.from(streamingClient.dataStream(HttpRequest.GET('/lifecycle/endless'))).blockFirst(Duration.ofSeconds(5))
        // closing the client closes the connection, which the server sees as a disconnect mid-stream
        streamingClient.close()

        then:
        first != null
        conditions.eventually {
            List<Timer> timers = serverTimers('/lifecycle/endless')
            assert timers.size() == 1
            assert timers[0].id.getTag('status') == '200'
            assert timers[0].count() == 1
        }

        cleanup:
        streamingClient?.close()
    }

    void "a client request cancelled before the response is recorded once"() {
        given:
        start()

        when:
        Disposable subscription = Flux.from(client.exchange(HttpRequest.GET('/lifecycle/slow'), String)).subscribe()
        Thread.sleep(300)
        subscription.dispose()

        then:
        conditions.eventually {
            List<Timer> timers = clientTimers()
            assert timers.size() == 1
            assert timers[0].id.getTag('status') == 'CLIENT_ERROR'
            assert timers[0].id.getTag('exception') == 'CancellationException'
            assert timers[0].count() == 1
        }
    }

    void "a client request that fails without a response is recorded once"() {
        given:
        start()
        int port = new ServerSocket(0).withCloseable { it.localPort }
        HttpClient unreachable = embeddedServer.applicationContext.createBean(HttpClient, new URL("http://localhost:$port"))

        when:
        unreachable.toBlocking().exchange('/lifecycle/ok', String)

        then:
        thrown(HttpClientException)
        conditions.eventually {
            List<Timer> timers = clientTimers()
            assert timers.size() == 1
            assert timers[0].id.getTag('status') == '500'
            assert timers[0].id.getTag('exception') != 'none'
            assert timers[0].count() == 1
        }

        cleanup:
        unreachable?.close()
    }

    void "client timers have the same tag keys with and without a service id (#1037)"() {
        given:
        start()
        LifecycleClient declarativeClient = embeddedServer.applicationContext.getBean(LifecycleClient)

        when:
        client.toBlocking().retrieve('/lifecycle/ok')
        declarativeClient.ok()

        then:
        List<Timer> timers = clientTimers()
        timers.size() == 2
        timers.collect { it.id.tags*.key as Set }.unique().size() == 1
        timers[0].id.tags*.key.containsAll(['exception', 'method', 'serviceId', 'status', 'uri'])
        timers*.id*.getTag('serviceId') as Set == ['none', 'embedded-server'] as Set
    }

    void "a non-standard status code is recorded"() {
        given:
        start()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/lifecycle/status', String)

        then:
        response.code() == 299
        conditions.eventually {
            List<Timer> timers = serverTimers('/lifecycle/status')
            assert timers.size() == 1
            assert timers[0].id.getTag('status') == '299'
        }
        clientTimers()*.id*.getTag('status') == ['299']
    }

    void "excluded paths are not recorded (#280)"() {
        given:
        start([
            'micronaut.metrics.http.exclusions'       : ['/lifecycle/excl.*'],
            'micronaut.metrics.http.client.exclusions': ['/lifecycle/excl.*', '/other']
        ])

        when:
        client.toBlocking().retrieve('/lifecycle/excluded')
        client.toBlocking().retrieve('/lifecycle/ok')

        then:
        conditions.eventually {
            assert serverTimers('/lifecycle/ok').size() == 1
        }
        serverTimers('/lifecycle/excluded').isEmpty()
        clientTimers().size() == 1
    }

    void "HTTP/2 responses are recorded once they were written"() {
        given:
        start([
            'micronaut.server.http-version'          : '2.0',
            'micronaut.server.ssl.enabled'           : false,
            'micronaut.http.client.plaintext-mode'   : 'h2c_prior_knowledge'
        ])
        long start = System.nanoTime()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/lifecycle/stream', String)
        long elapsed = System.nanoTime() - start

        then:
        response.body() == 'chunk-1\nchunk-2\nchunk-3\n'
        conditions.eventually {
            List<Timer> timers = serverTimers('/lifecycle/stream')
            assert timers.size() == 1
            assert timers[0].count() == 1
            assert timers[0].totalTime(TimeUnit.NANOSECONDS) >= Duration.ofMillis(250).toNanos()
            assert timers[0].totalTime(TimeUnit.NANOSECONDS) <= elapsed
        }

        when:
        client.toBlocking().exchange('/lifecycle/writer-error', String)

        then:
        thrown(HttpClientResponseException)
        conditions.eventually {
            List<Timer> timers = serverTimers('/lifecycle/writer-error')
            assert timers.size() == 1
            assert timers[0].id.getTag('status') == '500'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Client('/')
    static interface LifecycleClient {
        @Get('/lifecycle/ok')
        String ok()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/lifecycle')
    static class LifecycleController {

        @Get('/ok')
        String ok() { 'ok' }

        @Get('/excluded')
        String excluded() { 'excluded' }

        @Get('/status')
        HttpResponse<String> status() {
            HttpResponse.<String> status(299, 'Custom').body('custom')
        }

        @Get(value = '/writer-error', produces = MediaType.TEXT_PLAIN)
        FailingBody writerError() {
            new FailingBody(false)
        }

        @Get(value = '/codec-error', produces = MediaType.TEXT_PLAIN)
        FailingBody codecError() {
            new FailingBody(true)
        }

        @Get(value = '/stream', produces = MediaType.TEXT_PLAIN)
        Publisher<String> stream() {
            Flux.range(1, 3).delayElements(Duration.ofMillis(100)).map(i -> 'chunk-' + i + '\n')
        }

        @Get(value = '/endless', produces = MediaType.TEXT_PLAIN)
        Publisher<String> endless() {
            Flux.interval(Duration.ofMillis(20)).map(i -> 'tick-' + i + '\n')
        }

        @Get('/slow')
        Mono<String> slow() {
            Mono.delay(Duration.ofSeconds(2)).map(i -> 'slow')
        }
    }

    static class FailingBody {
        final boolean codec

        FailingBody(boolean codec) {
            this.codec = codec
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class FailingBodyWriter implements MessageBodyWriter<FailingBody> {
        @Override
        void writeTo(Argument<FailingBody> type, MediaType mediaType, FailingBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            if (object.codec) {
                throw new CodecException('cannot encode the body')
            }
            throw new IllegalStateException('cannot write the body')
        }
    }
}
