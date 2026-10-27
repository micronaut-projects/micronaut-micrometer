package io.micronaut.configuration.metrics.binder.logging

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.logging.Log4j2Metrics
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import spock.lang.Specification

class Log4j2MeterRegistryBinderFactorySpec extends Specification {

    void "log4j2 events are counted when log4j core is the log4j api implementation"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        MeterRegistry registry = ctx.getBean(MeterRegistry)
        Logger logger = LogManager.getLogger('log4j2-metrics-test')
        double before = registry.get('log4j2.events').tag('level', 'error').counter().count()

        when:
        logger.error('an error')
        logger.error('another error')

        then:
        ctx.containsBean(Log4j2Metrics)
        ctx.getBeanDefinition(Log4j2Metrics).stringValue(Bean, 'preDestroy').get() == 'close'
        registry.get('log4j2.events').tag('level', 'error').counter().count() == before + 2

        cleanup:
        ctx.close()
    }

    void "log4j2 metrics can be disabled: #config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(config)

        expect:
        !ctx.containsBean(Log4j2Metrics)

        cleanup:
        ctx.close()

        where:
        config << [['micronaut.metrics.binders.log4j2.enabled': false], ['micronaut.metrics.enabled': false]]
    }
}
