package io.micronaut.configuration.metrics.annotation

import io.micrometer.core.annotation.Timed
import io.micrometer.core.aop.MeterTag
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class MeterTagKotlinTest {

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Inject
    lateinit var service: MeterTagService

    @Test
    fun parameterAndResultTagsAreApplied() {
        service.lookup("acme")

        val timer = meterRegistry.get("kotlin.metertag")
            .tags("tenant", "acme", "label", "tenant-acme", "result", "found-acme")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun suspendFunctionsAreTaggedWithTheirResult() {
        runBlocking { service.lookupSuspend("acme") }

        val timer = meterRegistry.get("kotlin.metertag.suspend")
            .tags("tenant", "acme", "result", "suspended-acme")
            .timer()
        assertEquals(1, timer.count())
    }
}

@Singleton
open class MeterTagService {

    @Timed("kotlin.metertag")
    @MeterTag("result")
    @MeterTag(key = "label", expression = "#{ 'tenant-' + #tenant }")
    open fun lookup(@MeterTag("tenant") tenant: String): String = "found-$tenant"

    @Timed("kotlin.metertag.suspend")
    @MeterTag("result")
    open suspend fun lookupSuspend(@MeterTag("tenant") tenant: String): String {
        delay(1)
        return "suspended-$tenant"
    }
}
