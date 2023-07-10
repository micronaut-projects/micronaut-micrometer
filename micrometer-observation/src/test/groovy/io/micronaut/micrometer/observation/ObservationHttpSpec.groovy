package io.micronaut.micrometer.observation

import groovy.util.logging.Slf4j
import io.micrometer.observation.ObservationFilter
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.micrometer.observation.utils.ObservedReactorPropagation
import io.micronaut.reactor.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.rxjava2.http.client.RxHttpClient
import io.micronaut.scheduling.annotation.ExecuteOn
import io.reactivex.Single
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static io.micronaut.scheduling.TaskExecutors.IO

@Slf4j("LOG")
class ObservationHttpSpec extends Specification {

    String TRACING_ID = "X-TrackingId"

    @AutoCleanup
    private ApplicationContext context

    @AutoCleanup
    ReactorHttpClient reactorHttpClient

    @AutoCleanup
    HttpClient httpClient

    private PollingConditions conditions = new PollingConditions()

    @AutoCleanup
    private EmbeddedServer embeddedServer

    private TestObservationRegistry testObservationRegistry;

    void setup() {
        context = ApplicationContext.builder(
            'micronaut.application.name': 'test-app',
            'micrometer.observation.http.exclusions[0]': '.*exclude.*',
            'spec.name': 'ObservationHttpSpec',
            'micrometer.observations.common-key-value.common_key': 'common_value',
            'reactor.enableAutomaticContextPropagation': false
        ).start()

        embeddedServer = context.getBean(EmbeddedServer).start()
        reactorHttpClient = ReactorHttpClient.create(embeddedServer.URL)
        httpClient = HttpClient.create(embeddedServer.URL)
        def observationRegistry = embeddedServer.applicationContext.getBean(ObservationRegistry)
        testObservationRegistry = (TestObservationRegistry) observationRegistry
    }

    void 'test map WithSpan annotation'() {
        int count = 1

        expect:
        List<Tuple2> result = Flux.range(1, count)
                .flatMap {
                    String tracingId = UUID.randomUUID()
                    HttpRequest<Object> request = HttpRequest
                            .POST("/annotations/enter", new SomeBody())
                            .header(TRACING_ID, tracingId)
                    return Mono.from(reactorHttpClient.retrieve(request)).map(response -> {
                        Tuples.of(tracingId, response)
                    })
                }
                .collectList()
                .block()
        for (Tuple2 t : result) {
            assert t.getT1() == t.getT2()
        }
        conditions.eventually {

            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(10)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).forAllObservationsWithNameEqualTo("http.client.requests", (o) -> {
                o.hasLowCardinalityKeyValueWithKey("client.name")
                o.hasLowCardinalityKeyValueWithKey("method")
                o.hasLowCardinalityKeyValueWithKey("exception")
                o.hasLowCardinalityKeyValueWithKey("status")
                o.hasLowCardinalityKeyValueWithKey("uri")
                o.hasHighCardinalityKeyValueWithKey("http.url")
            })

            TestObservationRegistryAssert.assertThat(testObservationRegistry).forAllObservationsWithNameEqualTo("method.observed", (o) -> {
                o.hasLowCardinalityKeyValueWithKey("class")
                o.hasLowCardinalityKeyValueWithKey("method")
            })

            // check your observation
            TestObservationRegistryAssert.assertThat(testObservationRegistry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasObservationWithNameEqualTo("enter")
                    .that()
                    .hasContextualNameEqualTo("TestController#enter")
                    .hasLowCardinalityKeyValue("class", "TestController")
                    .hasLowCardinalityKeyValue("method", "enter")
                    .hasParentObservation()
                    .hasBeenStarted()
                    .hasBeenStopped()

            TestObservationRegistryAssert.assertThat(testObservationRegistry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasObservationWithNameEqualTo("method.observed")
                    .that()
                    .hasContextualNameEqualTo("TestController#test")
                    .hasLowCardinalityKeyValue("class", "TestController")
                    .hasLowCardinalityKeyValue("method", "test")
                    .hasParentObservation()
                    .hasBeenStarted()
                    .hasBeenStopped()

            TestObservationRegistryAssert.assertThat(testObservationRegistry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasObservationWithNameEqualTo("test-withspan-mapping")
                    .that()
                    .hasContextualNameEqualTo("TestController#methodWithSpan")
                    .hasLowCardinalityKeyValue("class", "TestController")
                    .hasLowCardinalityKeyValue("method", "methodWithSpan")
                    .hasParentObservation()
                    .hasBeenStarted()
                    .hasBeenStopped()

            TestObservationRegistryAssert.assertThat(testObservationRegistry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasNumberOfObservationsWithNameEqualTo("method.observed", 3)
        }

        cleanup:
        testObservationRegistry.clear()
    }

    void 'test context propagation'() {
        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange('/propagate/context', String)

        then:
        conditions.eventually {
            response
            response.body() == "contains micronaut.http.server.request: true"
        }
        cleanup:
        testObservationRegistry.clear()
    }

    void 'test observation rxjava2'() {

        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange('/rxjava2/test', String)

        then:
        conditions.eventually {
            response
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(4)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasContextualNameEqualTo("http get")
                it.hasNameEqualTo("http.client.requests")
                it.hasLowCardinalityKeyValue("client.name", "localhost")
                it.hasParentObservation()
            }
        }

        cleanup:
        testObservationRegistry.clear()
    }

    void 'test exclude endpoint'() {
        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange('/exclude/test', String)

        then:
        conditions.eventually {
            response
            TestObservationRegistryAssert.assertThat(testObservationRegistry).doesNotHaveAnyObservation()

        }

        cleanup:
        testObservationRegistry.clear()
    }

    void 'test error #desc, path=#path'() {
        when:
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange(path, String)

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error"

        conditions.eventually {
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(spanCount)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasContextualNameEqualTo("http get "+ path)
                it.hasNameEqualTo("http.server.requests")
                it.hasLowCardinalityKeyValue("uri", path)
                it.hasLowCardinalityKeyValue("status", "500")
                it.hasError()
            }
        }

        cleanup:
        testObservationRegistry.clear()
        where:
        path                                      | spanCount | desc
        '/error/publisher'                        | 2         | 'inside publisher'
        '/error/publisherErrorContinueSpan'       | 2         | 'inside continueSpan publisher'
        '/error/mono'                             | 2         | 'propagated through publisher'
        '/error/sync'                             | 2         | 'inside normal function'
        '/error/completionStage'                  | 2         | 'inside completionStage'
        '/error/completionStagePropagation'       | 2         | 'propagated through  completionStage'
        '/error/completionStageErrorContinueSpan' | 2         | 'inside normal method continueSpan'
    }

    void 'test error 404'() {
        when:
        def route = '/error/notFoundRoute'
        HttpResponse<String> response = reactorHttpClient.toBlocking().exchange(route, String)

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Not Found"
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(1)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasContextualNameEqualTo("http get")
                it.hasNameEqualTo("http.server.requests")
                it.hasLowCardinalityKeyValue("uri", "NOT_FOUND")
                it.hasLowCardinalityKeyValue("status", "404")
            }
        }
        cleanup:
        testObservationRegistry.clear()
    }

    void 'route match template is added as route attribute'() {
        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClient)

        expect:

        warehouseClient.order(UUID.randomUUID())
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(2)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasContextualNameEqualTo("http get /client/order/{orderId}")
                it.hasNameEqualTo("http.server.requests")
                it.hasLowCardinalityKeyValue("uri", "/client/order/{orderId}")
            }
        }

        cleanup:
        testObservationRegistry.clear()
    }

    void 'test filter for common low cardinality key values'() {
        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClient)

        expect:

        warehouseClient.order(UUID.randomUUID())
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(2)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasLowCardinalityKeyValue("common-key", "common_value")
            }
        }

        cleanup:
        testObservationRegistry.clear()
    }

    void 'query variables are not included in route template attribute'() {
        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClient)

        expect:

        warehouseClient.order(UUID.randomUUID(), UUID.randomUUID())

        conditions.eventually {
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(2)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasContextualNameEqualTo("http get /client/order/{orderId}")
                it.hasNameEqualTo("http.server.requests")
                it.hasLowCardinalityKeyValue("uri", "/client/order/{orderId}")
            }
        }

        cleanup:
        testObservationRegistry.clear()
    }

    void 'test continue nested HTTP observation - reactive'() {

        when:
        HttpResponse<String> response = httpClient.toBlocking().exchange('/propagate/nestedReactive/John', String)

        then:
        response.body() == 'John'

        and: 'all spans are finished'
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(3)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasContextualNameEqualTo("http get /propagate/nestedReactive/{name}")
                it.hasNameEqualTo("http.server.requests")
                it.hasLowCardinalityKeyValue("foo", "bar")
                it.hasLowCardinalityKeyValue("foo2", "bar2")
            }
        }

        cleanup:
        testObservationRegistry.clear()
    }

    void 'test continue nested HTTP observation - reactive 2'() {

        when:
        HttpResponse<String> response = httpClient.toBlocking().exchange('/propagate/nestedReactive2/John', String)

        then:
        response.body() == 'John'

        and: 'all spans are finished'
        conditions.eventually {
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(3)
            TestObservationRegistryAssert.assertThat(testObservationRegistry).hasAnObservation {
                it.hasContextualNameEqualTo("http get /propagate/nestedReactive2/{name}")
                it.hasNameEqualTo("http.server.requests")
                it.hasLowCardinalityKeyValue("foo", "bar")
                it.hasLowCardinalityKeyValue("foo3", "bar3")
            }
        }

        cleanup:
        testObservationRegistry.clear()
    }

    @Introspected
    static class SomeBody {
    }

    @Controller("/annotations")
    static class TestController {

        @Inject
        @Client("/")
        ReactorHttpClient reactorHttpClient

        @Inject
        ObservationRegistry observationRegistry

        @ExecuteOn(IO)
        @Post("/enter")
        @Observed(name = "enter")
        Mono<String> enter(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            LOG.debug("enter")
            return Mono.from(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/annotations/test")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ExecuteOn(IO)
        @Get("/test")
        @Observed
        Mono<String> test(@Header("X-TrackingId") String tracingId) {
            LOG.debug("test")
            privateMethodTest(tracingId)
            return Mono.from(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/annotations/test2")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @Observed
        void privateMethodTest(String traceId) {

        }

        @ExecuteOn(IO)
        @Get("/test2")
        Mono<String> test2(@Header("X-TrackingId") String tracingId) {
            LOG.debug("test2")
            methodWithSpan(tracingId).toCompletableFuture().get()
            return Mono.just(tracingId)
        }

        @Observed(name="test-withspan-mapping")
        CompletionStage<Void> methodWithSpan(String tracingId) {
            return CompletableFuture.runAsync(() -> {return normalFunctionWithNewSpan(tracingId)})
        }

        @Observed
        String normalFunctionWithNewSpan(String tracingId) {
            return tracingId
        }
    }

    @Controller('/propagate')
    static class ContextPropagateController {

        @Inject
        PropagateClient propagateClient

        @Inject
        ObservationRegistry observationRegistry

        @Get('/hello/{name}')
        String hello(String name) {
            return name
        }

        @Get("/context")
        Mono<String> context() {

            return Mono.deferContextual(ctx -> {
                boolean hasKey = ctx.hasKey(ServerRequestContext.KEY)
                int size = ctx.size()
                return Mono.just("contains ${ServerRequestContext.KEY}: $hasKey")
            }) as Mono<String>
        }

        @Get('/nestedReactive/{name}')
        @SingleResult
        Publisher<String> nestedReactive(String name) {

            def current = observationRegistry.getCurrentObservation()
            current.lowCardinalityKeyValue('foo', 'bar')
            return Flux.deferContextual { contextView ->
                Flux.from(propagateClient.continuedRx(name))
                        .flatMap({ String res ->
                            // Here thread switch can occur,
                            // that means the thread might be different and Span.current() wouldn't work
                            current.lowCardinalityKeyValue('foo2', 'bar2')
                            // NOTE: the span needs to be not closed for this attribute setting to work
                            return Mono.just(name)
                        })
            }
        }

        @Get('/nestedReactive2/{name}')
        @SingleResult
        Publisher<String> nestedReactive2(String name) {
            def current = observationRegistry.getCurrentObservation()
            current.lowCardinalityKeyValue('foo', 'bar')
            return Flux.deferContextual { contextView ->
                {
                        Flux.from(propagateClient.continuedRx(name))
                                .flatMap({ String res ->
                                    // Here thread switch can occur,
                                    // that means the thread might be different and Span.current() wouldn't work
                                    // We need can retrieve the current span from the Reactor context
                                    def currentInnerSpan = ObservedReactorPropagation.currentObservation(contextView)
                                    currentInnerSpan.lowCardinalityKeyValue('foo3', 'bar3')
                                    return Mono.just(name)
                                }).contextWrite(contextView)
                    }
                }
        }
    }

    @Client('/propagate')
    static interface PropagateClient {

        @Get('/hello/{name}')
        @SingleResult
        Publisher<String> continuedRx(String name)
    }

    @Controller('/error')
    static class ErrorController {

        @Get("/publisher")
        @Observed
        Mono<Void> publisher() {
            throw new RuntimeException("publisher")
        }

        @Get("/publisherErrorContinueSpan")
        Mono<Void> publisherErrorContinueSpan() {
            return Mono.from(continueSpanPublisher())
        }

        @Observed
        Mono<Void> continueSpanPublisher() {
            throw new RuntimeException("publisherErrorContinueSpan")
        }

        @Get("/mono")
        @Observed
        Mono<Void> mono() {
            return Mono.error(new RuntimeException("publisher"))
        }

        @Get("/sync")
        @Observed
        void sync() {
            throw new RuntimeException("sync")
        }

        @Get("/completionStage")
        @Observed
        CompletionStage<Void> completionStage(){
            throw new RuntimeException("completionStage")
        }

        @Get("/completionStagePropagation")
        @Observed
        CompletionStage<Void> completionStagePropagation (){
            return CompletableFuture.runAsync( ()-> { throw new RuntimeException("completionStage")})
        }

        @Get("/completionStageErrorContinueSpan")
        CompletionStage<Void> completionStageErrorContinueSpan () {
            throwAnError()
            return null
        }

        @Observed
        void throwAnError() {
            throw new RuntimeException("throwAnError")
        }
    }

    @Controller('/exclude')
    static class ExcludeController {

        @Get("/test")
        void excludeTest() {}
    }

    @Controller('/rxjava2')
    static class RxJava2 {

        @Inject
        @Client("/")
        RxHttpClient rxHttpClient

        @Get("/test")
        Single<String> test() {
            return Single.fromPublisher(
                    rxHttpClient.retrieve(HttpRequest
                            .GET("/rxjava2/test2"), String)
            )
        }

        @Observed
        @Get("/test2")
        Single<String> test2() {
            dummyMethodThatWillNotProduceSpan()
            return Single.just("test2")
        }

        void dummyMethodThatWillNotProduceSpan() {}

    }

    @Controller("/client")
    static class ClientController {

        @Get("/count")
        int getItemCount(@QueryValue String store, @QueryValue int upc) {
            return upc
        }


        @Post("/order")
        void order(Map<String, ?> json) {

        }

        @Get("/order/{orderId}")
        void order(@PathVariable("orderId") UUID orderId, @Nullable @QueryValue("customerId") UUID customerId) {

        }
    }

    @Client("/client")
    static interface WarehouseClient {

        @Get("/count")
        @Observed
        int getItemCount(@QueryValue String store, @QueryValue int upc);

        @Post("/order")
        @Observed
        void order(Map<String, ?> json);

        @Get("/order/{orderId}")
        void order(UUID orderId);

        @Get("/order/{orderId}?customerId={customerId}")
        void order(UUID orderId, UUID customerId);

    }

    @Client(id = "correctspanname")
    static interface WarehouseClientWithId {

        @Post("/order")
        @Observed
        void order(Map<String, ?> json);

    }

    @Factory
    @Requires(property = "spec.name", value = "ObservationHttpSpec")
    static class DefaultFactory {

        @Singleton
        @Primary
        ObservationRegistry observationRegistry(ObservationFilter observationFilter) {
            def registry = TestObservationRegistry.create()
            registry.observationConfig().observationFilter {observationFilter(it) }
            return registry
        }

    }

}
