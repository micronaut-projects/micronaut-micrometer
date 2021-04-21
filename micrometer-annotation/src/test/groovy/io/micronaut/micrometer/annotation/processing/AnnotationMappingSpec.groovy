package io.micronaut.micrometer.annotation.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted

class AnnotationMappingSpec extends AbstractTypeElementSpec {

    void 'test map timedset annotation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

@io.micrometer.core.annotation.Timed("foo")
@javax.inject.Singleton
class Test {

}
''')
        def bean = getBean(context, "test.Test")

        expect:
        bean instanceof Intercepted

    }

    void 'test map counted annotation'() {
        given:
        def context = buildContext('test.Test', '''
package test;

@javax.inject.Singleton
class Test {
    @io.micrometer.core.annotation.Counted("foo")
    void test() {
    }
}
''')
        def bean = getBean(context, "test.Test")

        expect:
        bean instanceof Intercepted
    }

}
