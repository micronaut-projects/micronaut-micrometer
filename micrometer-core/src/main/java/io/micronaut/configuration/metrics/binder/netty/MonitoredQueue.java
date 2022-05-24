/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.configuration.metrics.binder.netty;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micronaut.core.annotation.Internal;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.EXECUTION_TIME;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NETTY;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.NUMBER;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.QUEUE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.SIZE;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.WAIT_TIME;
import static io.micronaut.configuration.metrics.binder.netty.NettyMetrics.dot;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An instrumented Queue.
 *
 * @author croudet
 * @since 2.0
 */
@Internal
final class MonitoredQueue implements Queue<Runnable> {

    private final Queue<Runnable> delegate;
    private final MeterRegistry meterRegistry;
    private final Timer waitTimeTimer;
    private final Timer executionTimer;
    private final Timer globalWaitTimeTimer;
    private final Timer globalExecutionTimeTimer;
    private final Counter globalTaskCounter;

    /**
     * @param index                    An index.
     * @param meterRegistry            The meter registry.
     * @param tag                      A Tag.
     * @param globalTaskCounter        A counter.
     * @param globalWaitTimeTimer      The global wait time timer.
     * @param globalExecutionTimeTimer The global execution time timer.
     * @param queue                    The Queue.
     */
    MonitoredQueue(int index,
                   MeterRegistry meterRegistry,
                   Tag tag,
                   Counter globalTaskCounter,
                   Timer globalWaitTimeTimer,
                   Timer globalExecutionTimeTimer,
                   Queue<Runnable> queue) {
        this.delegate = queue;
        this.meterRegistry = meterRegistry;
        this.globalExecutionTimeTimer = globalExecutionTimeTimer;
        this.globalWaitTimeTimer = globalWaitTimeTimer;
        this.globalTaskCounter = globalTaskCounter;
        Tags tags = Tags.of(tag, Tag.of(QUEUE, SIZE))
                .and(NUMBER, Integer.toString(index));
        Gauge.builder(dot(NETTY, QUEUE, SIZE), delegate, Queue::size)
                .tags(tags)
                .description("The approximate number of tasks that are queued for execution.")
                .register(meterRegistry);

        tags = Tags.of(tag, Tag.of(QUEUE, WAIT_TIME))
                .and(NUMBER, Integer.toString(index));
        waitTimeTimer = Timer.builder(dot(NETTY, QUEUE, WAIT_TIME)).description("Wait time spent in the Queue.").publishPercentileHistogram().tags(tags).register(meterRegistry);

        tags = Tags.of(tag, Tag.of(QUEUE, EXECUTION_TIME))
                .and(NUMBER, Integer.toString(index));
        executionTimer = Timer.builder(dot(NETTY, QUEUE, EXECUTION_TIME)).description("Runnable execution time.").publishPercentileHistogram().tags(tags).register(meterRegistry);
    }

    @Override
    public void forEach(Consumer<? super Runnable> action) {
        delegate.forEach(action);
    }

    @Override
    public boolean add(Runnable e) {
        globalTaskCounter.increment();
        return delegate.add(new TimedRunnable(meterRegistry, executionTimer, waitTimeTimer, globalExecutionTimeTimer, globalWaitTimeTimer, e));
    }

    @Override
    public boolean offer(Runnable e) {
        globalTaskCounter.increment();
        return delegate.offer(new TimedRunnable(meterRegistry, executionTimer, waitTimeTimer, globalExecutionTimeTimer, globalWaitTimeTimer, e));
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Runnable remove() {
        return delegate.remove();
    }

    @Override
    public Runnable poll() {
        return delegate.poll();
    }

    @Override
    public Iterator<Runnable> iterator() {
        return delegate.iterator();
    }

    @Override
    public Runnable element() {
        return delegate.element();
    }

    @Override
    public Runnable peek() {
        return delegate.peek();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Runnable> c) {
        for (Runnable r : c) {
            if (!add(r)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean removeIf(Predicate<? super Runnable> filter) {
        return delegate.removeIf(filter);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public Spliterator<Runnable> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public Stream<Runnable> stream() {
        return delegate.stream();
    }

    @Override
    public Stream<Runnable> parallelStream() {
        return delegate.parallelStream();
    }

    /**
     * Runnable Wrapper that register time spent in queue and execution time.
     *
     * @since 2.0
     */
    static final class TimedRunnable implements Runnable {
        private final MeterRegistry registry;
        private final Timer executionTimer;
        private final Timer waitTimeTimer;
        private final Timer globalWaitTimeTimer;
        private final Timer globalExecutionTimeTimer;
        private final Runnable delegate;
        private final Timer.Sample idleSample;

        TimedRunnable(MeterRegistry registry,
                      Timer executionTimer,
                      Timer waitTimeTimer,
                      Timer globalExecutionTimeTimer,
                      Timer globalWaitTimeTimer,
                      Runnable delegate) {
            this.registry = registry;
            this.executionTimer = executionTimer;
            this.waitTimeTimer = waitTimeTimer;
            this.globalExecutionTimeTimer = globalExecutionTimeTimer;
            this.globalWaitTimeTimer = globalWaitTimeTimer;
            this.delegate = delegate;
            this.idleSample = Timer.start(registry);
        }

        @Override
        public void run() {
            globalWaitTimeTimer.record(idleSample.stop(waitTimeTimer), NANOSECONDS);
            final Timer.Sample executionSample = Timer.start(registry);
            try {
                delegate.run();
            } finally {
                globalExecutionTimeTimer.record(executionSample.stop(executionTimer), NANOSECONDS);
            }
        }
    }
}
