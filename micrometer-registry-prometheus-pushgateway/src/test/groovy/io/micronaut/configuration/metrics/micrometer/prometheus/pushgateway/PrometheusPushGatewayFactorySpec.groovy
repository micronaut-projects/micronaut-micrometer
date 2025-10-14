package io.micronaut.configuration.metrics.micrometer.prometheus.pushgateway

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.prometheus.metrics.exporter.pushgateway.PushGateway
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static PrometheusPushGatewayFactory.PROMETHEUS_PUSHGATEWAY_ENABLED

class PrometheusPushGatewayFactorySpec extends Specification {

    void "verify PushGateway is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 1
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['PrometheusMeterRegistry'])
        context.getBeansOfType(PushGateway)

        cleanup:
        context.stop()
    }

    @Unroll
    void "verify PrometheusMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(PushGateway).isPresent() == result

        cleanup:
        context.stop()

        where:
        cfg                                   | setting | result
        MICRONAUT_METRICS_ENABLED             | false   | false
        MICRONAUT_METRICS_ENABLED             | true    | true
        PROMETHEUS_PUSHGATEWAY_ENABLED        | true    | true
        PROMETHEUS_PUSHGATEWAY_ENABLED        | false   | false
    }
}
