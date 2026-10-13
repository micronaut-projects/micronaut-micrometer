package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.filter.ServerFilterPhase
import io.micronaut.micrometer.observation.http.server.ObservationServerFilter
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.codec.CodecException
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * When the HTTP observations are stopped: exactly once per request, after the server response was
 * written, and for every outcome of a client request.
 */
class ObservationHttpLifecycleSpec extends Specification {

    static final String SPEC_NAME = 'ObservationHttpLifecycleSpec'
    static final String SERVER = 'http.server.requests'
    static final String CLIENT = 'http.client.requests'

    PollingConditions conditions = new PollingConditions(timeout: 5)

    @AutoCleanup
    EmbeddedServer embeddedServer

    @AutoCleanup
    HttpClient client

    MeterRegistry registry

    void setup() {
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.application.name': 'test-app',
            'spec.name'                 : SPEC_NAME
        ])
        registry = embeddedServer.applicationContext.getBean(MeterRegistry)
        client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
    }

    private List<Timer> timers(String name, String uri) {
        registry.find(name).tag('uri', uri).timers() as List<Timer>
    }

    private int activeTasks(String name) {
        (registry.find(name + '.active').longTaskTimers() as List<LongTaskTimer>).sum { it.activeTasks() } as Integer ?: 0
    }

    void 'a response whose body cannot be written is observed once, with the replacing 500 response (#1233)'() {
        when:
        client.toBlocking().exchange('/lifecycle/writer-error', String)

        then:
        HttpClientResponseException e = thrown()
        e.code() == 500
        conditions.eventually {
            List<Timer> server = timers(SERVER, '/lifecycle/writer-error')
            assert server.size() == 1
            assert server[0].id.getTag('status') == '500'
            assert server[0].id.getTag('exception') == 'IllegalStateException'
            assert server[0].count() == 1
            assert activeTasks(SERVER) == 0
        }
    }

    void 'a successful error route that replaces a response whose body cannot be written is not an error'() {
        when: 'the exception handler response of the controller exception cannot be written and an error route recovers'
        HttpResponse<String> response = client.toBlocking().exchange('/lifecycle/recovered-writer-error', String)

        then:
        response.code() == 200
        response.body() == 'recovered'
        conditions.eventually {
            List<Timer> server = timers(SERVER, '/lifecycle/recovered-writer-error')
            assert server.size() == 1
            assert server[0].id.getTag('status') == '200'
            assert server[0].id.getTag('exception') == 'none'
            assert server[0].id.getTag('error') == 'none'
            assert server[0].count() == 1
            assert activeTasks(SERVER) == 0
        }
    }

    void 'a streamed response is observed until it completed'() {
        given:
        long start = System.nanoTime()

        when:
        String body = client.toBlocking().retrieve('/lifecycle/stream')
        long elapsed = System.nanoTime() - start

        then:
        body == 'chunk-1\nchunk-2\nchunk-3\n'
        conditions.eventually {
            List<Timer> server = timers(SERVER, '/lifecycle/stream')
            assert server.size() == 1
            assert server[0].count() == 1
            assert server[0].totalTime(TimeUnit.NANOSECONDS) >= Duration.ofMillis(250).toNanos()
            assert server[0].totalTime(TimeUnit.NANOSECONDS) <= elapsed
            assert activeTasks(SERVER) == 0
        }
    }

    void 'a streamed response cancelled by the client is observed once'() {
        given:
        StreamingHttpClient streamingClient = embeddedServer.applicationContext.createBean(StreamingHttpClient, embeddedServer.URL)

        when:
        def first = Flux.from(streamingClient.dataStream(HttpRequest.GET('/lifecycle/endless'))).blockFirst(Duration.ofSeconds(5))
        // closing the client closes the connection, which the server sees as a disconnect mid-stream
        streamingClient.close()

        then:
        first != null
        conditions.eventually {
            List<Timer> server = timers(SERVER, '/lifecycle/endless')
            assert server.size() == 1
            assert server[0].id.getTag('status') == '200'
            assert server[0].count() == 1
            assert activeTasks(SERVER) == 0
            assert activeTasks(CLIENT) == 0
        }

        cleanup:
        streamingClient?.close()
    }

    void 'a client observation is stopped for the error status #status (#1248)'() {
        when:
        client.toBlocking().exchange("/lifecycle/status/$status", String)

        then:
        HttpClientResponseException e = thrown()
        e.code() == status
        List<Timer> clientTimers = registry.find(CLIENT).tag('status', status as String).timers() as List<Timer>
        clientTimers.size() == 1
        clientTimers[0].id.getTag('exception') == 'HttpClientResponseException'
        clientTimers[0].count() == 1
        activeTasks(CLIENT) == 0
        conditions.eventually {
            List<Timer> server = timers(SERVER, '/lifecycle/status/{status}')
            assert server.size() == 1
            assert server[0].id.getTag('status') == status as String
            assert activeTasks(SERVER) == 0
        }

        where:
        status << [400, 500, 499, 520]
    }

    void 'a non-standard success status is observed'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange('/lifecycle/status/299', String)

        then:
        response.code() == 299
        (registry.find(CLIENT).timers() as List<Timer>)*.id*.getTag('status') == ['299']
        conditions.eventually {
            assert timers(SERVER, '/lifecycle/status/{status}')*.id*.getTag('status') == ['299']
        }
    }

    void 'a client observation is stopped when the request fails without a response'() {
        given:
        int port = new ServerSocket(0).withCloseable { it.localPort }
        HttpClient unreachable = embeddedServer.applicationContext.createBean(HttpClient, new URL("http://localhost:$port"))

        when:
        unreachable.toBlocking().exchange('/lifecycle/ok', String)

        then:
        thrown(HttpClientException)
        List<Timer> clientTimers = registry.find(CLIENT).timers() as List<Timer>
        clientTimers.size() == 1
        clientTimers[0].id.getTag('status') == 'CLIENT_ERROR'
        clientTimers[0].id.getTag('exception') != 'none'
        activeTasks(CLIENT) == 0

        cleanup:
        unreachable?.close()
    }

    void 'a client observation is stopped when the request is cancelled'() {
        when:
        Disposable subscription = Flux.from(client.exchange(HttpRequest.GET('/lifecycle/slow'), String)).subscribe()
        Thread.sleep(300)
        subscription.dispose()

        then:
        conditions.eventually {
            List<Timer> clientTimers = registry.find(CLIENT).timers() as List<Timer>
            assert clientTimers.size() == 1
            assert clientTimers[0].id.getTag('status') == 'CLIENT_ERROR'
            assert clientTimers[0].count() == 1
            assert activeTasks(CLIENT) == 0
        }

        and: 'the server observation is stopped once the response is written'
        conditions.eventually {
            assert timers(SERVER, '/lifecycle/slow').size() == 1
            assert activeTasks(SERVER) == 0
        }
    }

    void 'the server filter runs in the metrics phase'() {
        expect:
        embeddedServer.applicationContext.getBean(ObservationServerFilter).order == ServerFilterPhase.METRICS.order()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/lifecycle')
    static class LifecycleController {

        @Get('/ok')
        String ok() { 'ok' }

        @Get('/status/{status}')
        HttpResponse<String> status(int status) {
            HttpResponse.<String> status(status, 'Custom').body('custom')
        }

        @Get(value = '/writer-error', produces = MediaType.TEXT_PLAIN)
        FailingBody writerError() {
            new FailingBody()
        }

        @Get('/recovered-writer-error')
        String recoveredWriterError() {
            throw new BusinessException()
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
            Mono.delay(Duration.ofSeconds(1)).map(i -> 'slow')
        }
    }

    static class FailingBody {
    }

    static class RecoverableBody {
    }

    static class BusinessException extends RuntimeException {
    }

    static class RecoverableWriteException extends RuntimeException {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Produces
    static class BusinessExceptionHandler implements ExceptionHandler<BusinessException, HttpResponse<?>> {
        @Override
        HttpResponse<?> handle(HttpRequest request, BusinessException exception) {
            HttpResponse.badRequest(new RecoverableBody()).contentType(MediaType.TEXT_PLAIN_TYPE)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class RecoverableBodyWriter implements MessageBodyWriter<RecoverableBody> {
        @Override
        void writeTo(Argument<RecoverableBody> type, MediaType mediaType, RecoverableBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new RecoverableWriteException()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/recovery')
    static class RecoveryController {

        @Error(global = true, exception = RecoverableWriteException)
        HttpResponse<String> recover() {
            HttpResponse.ok('recovered')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class FailingBodyWriter implements MessageBodyWriter<FailingBody> {
        @Override
        void writeTo(Argument<FailingBody> type, MediaType mediaType, FailingBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new IllegalStateException('cannot write the body')
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class TestFactory {

        @Singleton
        @Primary
        MeterRegistry meterRegistry() {
            new SimpleMeterRegistry()
        }
    }
}
