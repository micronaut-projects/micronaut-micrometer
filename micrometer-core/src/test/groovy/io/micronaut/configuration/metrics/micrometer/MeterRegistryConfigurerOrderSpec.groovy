package io.micronaut.configuration.metrics.micrometer

import io.micronaut.configuration.metrics.aggregator.CompositeMeterRegistryConfigurer
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import spock.lang.Specification


class MeterRegistryConfigurerOrderSpec extends Specification {

    void "verify beans created by in correct order"() {
        when:
        ApplicationContext ctx = ApplicationContext.run([
                "test.properties.enabled": true
        ])

        List<MeterRegistryConfigurer> configurerList = ctx.getBeansOfType(MeterRegistryConfigurer)

        then:
        configurerList.any { it.getClass() == CompositeMeterRegistryConfigurer.class }
        configurerList.last().getClass() == CompositeMeterRegistryConfigurer.class

        cleanup:
        ctx.close()
    }
}
