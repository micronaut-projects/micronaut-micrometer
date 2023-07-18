package io.micronaut.micrometer.observation

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.configuration.metrics.binder.web.ClientRequestMetricRegistryFilter
import io.micronaut.configuration.metrics.binder.web.ServerRequestMeterRegistryFilter
import io.micronaut.micrometer.observation.http.client.ObservationClientFilter
import io.micronaut.micrometer.observation.http.server.ObservationServerFilter
import spock.lang.Specification

class FilterCreationSpec extends Specification{

    void 'by default only observation filters are created'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
        ).start()

        then:
        context.getBeansOfType(MeterRegistry).size() == 1
        context.getBeansOfType(ObservationClientFilter).size() == 1
        context.getBeansOfType(ObservationServerFilter).size() == 1
        context.getBeansOfType(ClientRequestMetricRegistryFilter).size() == 0
        context.getBeansOfType(ServerRequestMeterRegistryFilter).size() == 0
    }

    void 'if micrometer observation client filter is disabled enable web client metrics by default'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
                'micrometer.observation.http.client.enabled': false
        ).start()

        then:
        context.getBeansOfType(MeterRegistry).size() == 1
        context.getBeansOfType(ObservationClientFilter).size() == 0
        context.getBeansOfType(ObservationServerFilter).size() == 1
        context.getBeansOfType(ClientRequestMetricRegistryFilter).size() == 1
        context.getBeansOfType(ServerRequestMeterRegistryFilter).size() == 0
    }

    void 'if micrometer observation server filter is disabled enable web server metrics by default'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
                'micrometer.observation.http.server.enabled': false
        ).start()

        then:
        context.getBeansOfType(MeterRegistry).size() == 1
        context.getBeansOfType(ObservationClientFilter).size() == 1
        context.getBeansOfType(ObservationServerFilter).size() == 0
        context.getBeansOfType(ClientRequestMetricRegistryFilter).size() == 0
        context.getBeansOfType(ServerRequestMeterRegistryFilter).size() == 1
    }

    void 'no filters should exists if both observation and web metrics are disabled'() {
        when:
        def context = io.micronaut.context.ApplicationContext.builder(
                'micronaut.application.name': 'test-app',
                'micrometer.observation.http.client.enabled': false,
                'micrometer.observation.http.server.enabled': false,
                'micronaut.metrics.binders.web.enabled': false
        ).start()

        then:
        context.getBeansOfType(MeterRegistry).size() == 1
        context.getBeansOfType(ObservationClientFilter).size() == 0
        context.getBeansOfType(ObservationServerFilter).size() == 0
        context.getBeansOfType(ClientRequestMetricRegistryFilter).size() == 0
        context.getBeansOfType(ServerRequestMeterRegistryFilter).size() == 0
    }

}
