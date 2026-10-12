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
package io.micronaut.micrometer.test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.assertj.core.api.AbstractAssert;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * AssertJ assertions for a {@link MeterRegistry}.
 *
 * <pre>
 * MeterRegistryAssert.assertThat(meterRegistry)
 *     .hasMeterWithName("http.server.requests")
 *     .hasTimerWithNameAndTags("http.server.requests", Tags.of("uri", "/books"), 1)
 *     .doesNotHaveMeterWithName("http.client.requests");
 * </pre>
 *
 * @author Graeme Rocher
 * @since 6.2.0
 */
public final class MeterRegistryAssert extends AbstractAssert<MeterRegistryAssert, MeterRegistry> {

    private MeterRegistryAssert(MeterRegistry actual) {
        super(actual, MeterRegistryAssert.class);
    }

    /**
     * Creates the assertions for a meter registry.
     *
     * @param meterRegistry The meter registry
     * @return The assertions
     */
    public static MeterRegistryAssert assertThat(MeterRegistry meterRegistry) {
        return new MeterRegistryAssert(meterRegistry);
    }

    /**
     * Verifies that the registry has a meter with the given name.
     *
     * @param name The meter name
     * @return This assertion
     */
    public MeterRegistryAssert hasMeterWithName(String name) {
        isNotNull();
        if (actual.find(name).meter() == null) {
            failWithMessage("Expected a meter with name <%s> but found only <%s>", name, meterNames());
        }
        return this;
    }

    /**
     * Verifies that the registry has no meter with the given name.
     *
     * @param name The meter name
     * @return This assertion
     */
    public MeterRegistryAssert doesNotHaveMeterWithName(String name) {
        isNotNull();
        Collection<Meter> meters = actual.find(name).meters();
        if (!meters.isEmpty()) {
            failWithMessage("Expected no meter with name <%s> but found <%s>", name, ids(meters));
        }
        return this;
    }

    /**
     * Verifies that the registry has a meter with the given name and at least the given tags.
     *
     * @param name The meter name
     * @param tags The tags
     * @return This assertion
     */
    public MeterRegistryAssert hasMeterWithNameAndTags(String name, Tags tags) {
        isNotNull();
        if (actual.find(name).tags(tags).meter() == null) {
            failWithMessage("Expected a meter with name <%s> and tags <%s> but found <%s>", name, tags, ids(actual.find(name).meters()));
        }
        return this;
    }

    /**
     * Verifies that the registry has no meter with the given name and at least the given tags.
     *
     * @param name The meter name
     * @param tags The tags
     * @return This assertion
     */
    public MeterRegistryAssert doesNotHaveMeterWithNameAndTags(String name, Tags tags) {
        isNotNull();
        Collection<Meter> meters = actual.find(name).tags(tags).meters();
        if (!meters.isEmpty()) {
            failWithMessage("Expected no meter with name <%s> and tags <%s> but found <%s>", name, tags, ids(meters));
        }
        return this;
    }

    /**
     * Verifies that the registry has a timer with the given name and at least the given tags that
     * recorded the given number of events.
     *
     * @param name  The timer name
     * @param tags  The tags
     * @param count The expected number of recorded events
     * @return This assertion
     */
    public MeterRegistryAssert hasTimerWithNameAndTags(String name, Tags tags, long count) {
        hasMeterWithNameAndTags(name, tags);
        Timer timer = actual.find(name).tags(tags).timer();
        if (timer == null) {
            failWithMessage("Expected a timer with name <%s> and tags <%s> but the meter is not a timer", name, tags);
        } else if (timer.count() != count) {
            failWithMessage("Expected timer <%s> to have recorded <%s> events but it recorded <%s>", timer.getId(), count, timer.count());
        }
        return this;
    }

    /**
     * Verifies that the registry has a counter with the given name and at least the given tags
     * with the given count.
     *
     * @param name  The counter name
     * @param tags  The tags
     * @param count The expected count
     * @return This assertion
     */
    public MeterRegistryAssert hasCounterWithNameAndTags(String name, Tags tags, double count) {
        hasMeterWithNameAndTags(name, tags);
        Counter counter = actual.find(name).tags(tags).counter();
        if (counter == null) {
            failWithMessage("Expected a counter with name <%s> and tags <%s> but the meter is not a counter", name, tags);
        } else if (Double.compare(counter.count(), count) != 0) {
            failWithMessage("Expected counter <%s> to have count <%s> but it has <%s>", counter.getId(), count, counter.count());
        }
        return this;
    }

    private String meterNames() {
        return actual.getMeters().stream().map(meter -> meter.getId().getName()).distinct().sorted().collect(Collectors.joining(", "));
    }

    private static String ids(Collection<Meter> meters) {
        return meters.stream().map(meter -> meter.getId().toString()).collect(Collectors.joining(", "));
    }
}
