package io.micronaut.micrometer.annotation.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted

class GroovyAnnotationMappingSpec extends AbstractTypeElementSpec {

    void 'test map timedset annotation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

@io.micrometer.core.annotation.Timed("foo")
@jakarta.inject.Singleton
class Test {

}
''')
        def bean = context.getBean(context.classLoader.loadClass( "test.Test"))

        expect:
        bean instanceof Intercepted

        cleanup:
        context.close()
    }

    void 'test map observed annotation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

@io.micrometer.observation.annotation.Observed(name="foo")
@jakarta.inject.Singleton
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
