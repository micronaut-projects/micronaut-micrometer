/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.annotation;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Property(name = "spec.name", value = "CountedMicronautTest")
@MicronautTest
class CountedMicronautTest {

    @Inject
    CountedMicronautTestService service;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void countedMethodPublishesCounter() {
        assertEquals("hello", service.countedMethod());

        Counter counter = meterRegistry.find("counted.test.junit").counter();
        assertNotNull(counter);
        assertEquals(1.0d, counter.count(), 1.0e-9d);
    }

    @Test
    void timedMethodPublishesTimer() {
        assertEquals("hello", service.timedMethod());

        Timer timer = meterRegistry.find("timed.test.junit").timer();
        assertNotNull(timer);
        assertEquals(1L, timer.count());
    }

    @Requires(property = "spec.name", value = "CountedMicronautTest")
    @Singleton
    static class CountedMicronautTestService {

        @Counted("counted.test.junit")
        public String countedMethod() {
            return "hello";
        }

        @Timed("timed.test.junit")
        public String timedMethod() {
            return "hello";
        }
    }
}
