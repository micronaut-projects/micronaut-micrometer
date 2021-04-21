package io.micronaut.micrometer.annotation.processing

import io.micronaut.aop.Intercepted
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import spock.lang.PendingFeature

class GroovyAnnotationMappingSpec extends AbstractBeanDefinitionSpec {

    @PendingFeature(reason = "Needs https://github.com/micronaut-projects/micronaut-core/pull/5282")
    void 'test map timedset annotation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

@io.micrometer.core.annotation.Timed("foo")
@javax.inject.Singleton
class Test {

}
''')
        def bean = context.getBean(context.classLoader.loadClass( "test.Test"))

        expect:
        bean instanceof Intercepted

        cleanup:
        context.close()
    }
}
