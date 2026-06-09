package io.micronaut.configuration.metrics.binder.r2dbc

import io.micronaut.context.ApplicationContext
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.PoolMetrics
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.net.URL
import java.net.URLClassLoader

class R2dbcPoolMetricsBinderFactorySpec extends Specification {

    void "test getting metrics from r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def pool = Mock(ConnectionPool)
        def metrics = new StubMetrics()
        pool.getMetrics() >> Optional.of(metrics)

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", pool)

        then:
        meterBinder.@poolMetrics == metrics
    }

    void "test getting empty metrics from r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def pool = Mock(ConnectionPool)
        pool.getMetrics() >> Optional.empty()

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", pool)

        then:
        meterBinder.@poolMetrics == null
    }

    void "test ignoring metrics when factory is not r2dbc pool"() {
        given:
        def r2dbcPoolMetricsBinderFactory = new R2dbcPoolMetricsBinderFactory()
        def factory = Mock(StubFactory)

        when:
        def meterBinder = r2dbcPoolMetricsBinderFactory.r2dbcPoolMeterBinder("foo", factory)

        then:
        meterBinder.@poolMetrics == null
    }

    void "test starting context without r2dbc pool on classpath"() {
        given:
        def originalClassLoader = Thread.currentThread().contextClassLoader
        def classLoader = new FilteringClassLoader(originalClassLoader)
        ApplicationContext context = ApplicationContext.builder()
            .classLoader(classLoader)
            .build()

        Thread.currentThread().contextClassLoader = classLoader

        when:
        context.registerSingleton(ConnectionFactory, new StubFactory())
        context.start()

        then:
        noExceptionThrown()
        !context.containsBean(classLoader.loadClass('io.micronaut.configuration.metrics.binder.r2dbc.R2dbcPoolMetricsBinderFactory'))
        !context.containsBean(classLoader.loadClass('io.micronaut.configuration.metrics.binder.r2dbc.R2dbcPoolMetricsBinder'))

        cleanup:
        context?.close()
        Thread.currentThread().contextClassLoader = originalClassLoader
        classLoader?.close()
    }

    class StubFactory implements ConnectionFactory {
        Publisher<? extends Connection> create() { null }
        ConnectionFactoryMetadata getMetadata() { null }
    }

    class StubMetrics implements PoolMetrics {
        int acquiredSize() { 0 }
        int allocatedSize() { 0 }
        int idleSize() { 0 }
        int pendingAcquireSize() { 0 }
        int getMaxAllocatedSize() { 0 }
        int getMaxPendingAcquireSize() { 0 }
    }

    private static final class FilteringClassLoader extends URLClassLoader {

        private static final String BINDER_PACKAGE = "io.micronaut.configuration.metrics.binder.r2dbc."

        FilteringClassLoader(ClassLoader parent) {
            super(classpathUrls(), parent)
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("io.r2dbc.pool.")) {
                throw new ClassNotFoundException(name)
            }
            Class<?> loadedClass = findLoadedClass(name)
            if (loadedClass == null && name.startsWith(BINDER_PACKAGE)) {
                try {
                    loadedClass = findClass(name)
                } catch (ClassNotFoundException ignored) {
                    loadedClass = super.loadClass(name, false)
                }
            } else if (loadedClass == null) {
                loadedClass = super.loadClass(name, false)
            }
            if (resolve) {
                resolveClass(loadedClass)
            }
            return loadedClass
        }

        private static URL[] classpathUrls() {
            System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .findAll { !it.contains("r2dbc-pool") }
                .collect { new File(it).toURI().toURL() } as URL[]
        }
    }
}
