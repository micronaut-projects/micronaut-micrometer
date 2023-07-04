package io.micronaut.micrometer.observation

import groovy.util.logging.Slf4j
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.reactor.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static io.micronaut.scheduling.TaskExecutors.IO

@Slf4j("LOG")
class AnnotationSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.application.name': 'test-app',
            'spec.name': 'AnnotationSpec'
    ])

    @Shared
    @AutoCleanup
    ReactorHttpClient reactorHttpClient = ReactorHttpClient.create(embeddedServer.URL)

    private PollingConditions conditions = new PollingConditions(timeout: 10)


    void 'test map WithSpan annotation'() {
        int count = 1
        // 2x Method call 1x NewSpan, 1x WithSpan  = 2
        int spanNumbers = 2
        def observationRegistry = embeddedServer.applicationContext.getBean(ObservationRegistry)
        def testObservationRegistry = (TestObservationRegistry) observationRegistry

        when:
        List<Tuple2> result = Flux.range(1, count)
                .flatMap {
                    String tracingId = UUID.randomUUID()
                    HttpRequest<Object> request = HttpRequest
                            .POST("/annotations/enter", new SomeBody())
                            .header("X-TrackingId", tracingId)
                    return Mono.from(reactorHttpClient.retrieve(request)).map(response -> {
                        Tuples.of(tracingId, response)
                    })
                }
                .collectList()
                .block()

        then:
        for (Tuple2 t : result) {
            assert t.getT1() == t.getT2()
        }

        conditions.eventually {

            // check your observation
            TestObservationRegistryAssert.assertThat(testObservationRegistry)
                    .doesNotHaveAnyRemainingCurrentObservation()
                    .hasObservationWithNameEqualTo("foo")
                    .that()
                    .hasHighCardinalityKeyValue("highTag", "highTagValue")
                    .hasLowCardinalityKeyValue("lowTag", "lowTagValue")
                    .hasBeenStarted()
                    .hasBeenStopped();

//            testExporter.finishedSpanItems.size() == count * spanNumbers
//
//            testExporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "tracing-annotation-span-attribute"))
//            !testExporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "tracing-annotation-span-tag-no-withspan"))
//            testExporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "tracing-annotation-span-tag-with-withspan"))
//            testExporter.finishedSpanItems.attributes.any(x -> x.asMap().keySet().any(y -> y.key == "tracing-annotation-span-tag-continue-span"))
//            // test if newspan has appended name
//            testExporter.finishedSpanItems.name.any(x -> x.contains("#test-withspan-mapping"))
//            testExporter.finishedSpanItems.name.any(x -> x.contains("#enter"))
        }

    }

//    void 'client with tracing annotations' () {
//        def testExporter = embeddedServer.applicationContext.getBean(InMemorySpanExporter)
//        def warehouseClient = embeddedServer.applicationContext.getBean(WarehouseClient)
//
//        when:
//        warehouseClient.order(Collections.singletonMap("testOrderKey", "testOrderValue"))
//        def res = warehouseClient.getItemCount("testItemCount", 10)
//
//        then:
//        res == 10
//        conditions.eventually {
//            testExporter.finishedSpanItems.size() == 1
//            testExporter.finishedSpanItems.get(0).name == "WarehouseClient.order"
//            testExporter.finishedSpanItems.get(0).attributes.get(AttributeKey.stringKey("warehouse.order")) == "{testOrderKey=testOrderValue}"
//        }
//
//        cleanup:
//        testExporter.reset()
//    }


    @Introspected
    static class SomeBody {
    }

    @Requires(property = "spec.name", value = "AnnotationSpec")
    @Controller("/annotations")
    static class TestController {

        @Inject
        @Client("/")
        private ReactorHttpClient reactorHttpClient

        @ExecuteOn(IO)
        @Post("/enter")
        @Observed(name = "enter")
        Mono<String> enter(@Header("X-TrackingId") String tracingId,
                           @Body SomeBody body) {
            LOG.debug("enter")
            return test(tracingId)
        }

        @ExecuteOn(IO)
        @Get("/test")
        @Observed
        Mono<String> test(@Header("X-TrackingId") String tracingId) {
            LOG.debug("test")
            return Mono.from(
                    reactorHttpClient.retrieve(HttpRequest
                            .GET("/annotations/test2")
                            .header("X-TrackingId", tracingId), String)
            )
        }

        @ExecuteOn(IO)
        @Get("/test2")
        Mono<String> test2(@Header("X-TrackingId") String tracingId) {
            LOG.debug("test2")
            return methodWithSpan(tracingId)
        }

        @Observed(name = "test-withspan-mapping")
        Mono<String> methodWithSpan(String tracingId) {
            return Mono.just(methodContinueSpan(tracingId))
        }

        @Observed
        String methodContinueSpan(String tracingId) {
            return tracingId
        }
    }

    @Requires(property = "spec.name", value = "AnnotationSpec")
    @Controller("/client")
    static class ClientController {

        @Get("/count")
        int getItemCount(@QueryValue String store, @QueryValue int upc) {
            return upc
        }


        @Post("/order")
        void order(Map<String, ?> json) {

        }

    }

    @Requires(property = "spec.name", value = "AnnotationSpec")
    @Client("/client")
    static interface WarehouseClient {

        @Get("/count")
        @Observed
        int getItemCount(@QueryValue String store, @QueryValue int upc);

        @Post("/order")
        @Observed
        void order(Map<String, ?> json);

    }

    @Factory
    @Requires(property = "spec.name", value = "AnnotationSpec")
    static class DefaultFactory {

        @Singleton
        @Primary
        ObservationRegistry observationRegistry() {
            return TestObservationRegistry.create()
        }

    }

}
