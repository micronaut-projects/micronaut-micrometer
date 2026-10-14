package io.micronaut.micrometer.observation.grpc

import io.grpc.CallOptions
import io.grpc.ClientInterceptor
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.ServerServiceDefinition
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.micrometer.common.KeyValues
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.grpc.DefaultGrpcServerObservationConvention
import io.micrometer.core.instrument.binder.grpc.GrpcServerObservationContext
import io.micrometer.core.instrument.binder.grpc.GrpcServerObservationConvention
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor
import io.micrometer.core.instrument.binder.grpc.MetricCollectingServerInterceptor
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ObservationGrpcInterceptorFactorySpec extends Specification {

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER = new MethodDescriptor.Marshaller<String>() {
        @Override
        InputStream stream(String value) {
            new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))
        }

        @Override
        String parse(InputStream stream) {
            new String(stream.readAllBytes(), StandardCharsets.UTF_8)
        }
    }

    private static final MethodDescriptor<String, String> ECHO = MethodDescriptor.<String, String> newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName('test.Echo', 'Echo'))
            .setRequestMarshaller(STRING_MARSHALLER)
            .setResponseMarshaller(STRING_MARSHALLER)
            .build()

    void "observation interceptors replace the metric collecting interceptors by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.containsBean(ObservationGrpcServerInterceptor)
        ctx.containsBean(ObservationGrpcClientInterceptor)
        !ctx.containsBean(MetricCollectingServerInterceptor)
        !ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }

    void "disabling server observations brings back the server metric collecting interceptor"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micrometer.observation.grpc.server.enabled': false])

        expect:
        !ctx.containsBean(ObservationGrpcServerInterceptor)
        ctx.containsBean(MetricCollectingServerInterceptor)
        ctx.containsBean(ObservationGrpcClientInterceptor)
        !ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }

    void "disabling client observations brings back the client metric collecting interceptor"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micrometer.observation.grpc.client.enabled': false])

        expect:
        ctx.containsBean(ObservationGrpcServerInterceptor)
        !ctx.containsBean(MetricCollectingServerInterceptor)
        !ctx.containsBean(ObservationGrpcClientInterceptor)
        ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }

    void "gRPC calls are observed and recorded once"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ObservationGrpcInterceptorFactorySpec'])
        MeterRegistry registry = ctx.getBean(MeterRegistry)
        String name = InProcessServerBuilder.generateName()
        ServerServiceDefinition service = ServerServiceDefinition.builder('test.Echo')
                .addMethod(ECHO, ServerCalls.asyncUnaryCall({ String request, observer ->
                    observer.onNext('echo ' + request)
                    observer.onCompleted()
                } as ServerCalls.UnaryMethod<String, String>))
                .build()
        List<ServerInterceptor> serverInterceptors = ctx.getBeansOfType(ServerInterceptor).toList()
        List<ClientInterceptor> clientInterceptors = ctx.getBeansOfType(ClientInterceptor).toList()
        Server server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(ServerInterceptors.intercept(service, serverInterceptors))
                .build()
                .start()
        ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build()

        when:
        String response = ClientCalls.blockingUnaryCall(ClientInterceptors.intercept(channel, clientInterceptors), ECHO, CallOptions.DEFAULT, 'hello')

        then:
        response == 'echo hello'
        serverInterceptors.size() == 1
        clientInterceptors.size() == 1
        registry.get('grpc.server').tag('rpc.method', 'Echo').tag('custom', 'value').timer().count() == 1
        registry.get('grpc.client').tag('rpc.method', 'Echo').timer().count() == 1
        !registry.find('grpc.server.processing.duration').timer()
        !registry.find('grpc.client.processing.duration').timer()

        cleanup:
        channel?.shutdownNow()
        server?.shutdownNow()
        ctx.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ObservationGrpcInterceptorFactorySpec')
    static class CustomServerConvention extends DefaultGrpcServerObservationConvention implements GrpcServerObservationConvention {
        @Override
        KeyValues getLowCardinalityKeyValues(GrpcServerObservationContext context) {
            super.getLowCardinalityKeyValues(context).and('custom', 'value')
        }
    }
}
