package io.micronaut.configuration.metrics.binder.grpc

import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor
import io.micrometer.core.instrument.binder.grpc.MetricCollectingServerInterceptor
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class GrpcMetricsBinderSpec extends Specification {

    void "test beans are created by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.containsBean(MetricCollectingServerInterceptor)
        ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }

    void "test disabling all grpc metrics"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "micronaut.metrics.binders.grpc.enabled": false
        ])

        expect:
        !ctx.containsBean(MetricCollectingServerInterceptor)
        !ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }

    void "test disabling all metrics"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "micronaut.metrics.enabled": false
        ])

        expect:
        !ctx.containsBean(MetricCollectingServerInterceptor)
        !ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }

    void "test disabling client grpc metrics"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "micronaut.metrics.binders.grpc.client.enabled": false
        ])

        expect:
        ctx.containsBean(MetricCollectingServerInterceptor)
        !ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }

    void "test disabling server grpc metrics"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                "micronaut.metrics.binders.grpc.server.enabled": false
        ])

        expect:
        !ctx.containsBean(MetricCollectingServerInterceptor)
        ctx.containsBean(MetricCollectingClientInterceptor)

        cleanup:
        ctx.close()
    }
}
