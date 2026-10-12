package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.annotation.Counted
import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MicronautTest
class SuspendMetricsTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Inject
    lateinit var service: SuspendService

    @Test
    fun recordFailuresOnlyIsHonouredForSuspendFunctions() {
        runBlocking { service.countFailuresOnly(false) }

        assertNull(meterRegistry.find("suspend.counted.failures").counter())

        assertThrows<IllegalStateException> {
            runBlocking { service.countFailuresOnly(true) }
        }

        val counter = meterRegistry.get("suspend.counted.failures")
            .tags("result", "failure", "exception", "IllegalStateException")
            .counter()
        assertEquals(1.0, counter.count())
        assertEquals(1, meterRegistry.find("suspend.counted.failures").counters().size)
    }

    @Test
    fun suspendFunctionFailuresAreTimed() {
        assertThrows<IllegalStateException> {
            runBlocking { service.timedFailure() }
        }

        val timer = meterRegistry.get("suspend.timed.failure")
            .tags("exception", "IllegalStateException")
            .timer()
        assertEquals(1, timer.count())
    }
}

@Singleton
open class SuspendService {

    @Counted(value = "suspend.counted.failures", recordFailuresOnly = true)
    open suspend fun countFailuresOnly(fail: Boolean): Int {
        delay(1)
        if (fail) {
            throw IllegalStateException("boom")
        }
        return 1
    }

    @Timed("suspend.timed.failure")
    open suspend fun timedFailure(): Int {
        delay(1)
        throw IllegalStateException("boom")
    }
}
