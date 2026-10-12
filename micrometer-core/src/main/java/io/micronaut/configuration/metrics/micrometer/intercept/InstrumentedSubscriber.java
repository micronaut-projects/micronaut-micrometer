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
package io.micronaut.configuration.metrics.micrometer.intercept;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Subscriber that notifies {@link #onStart()} when subscribed and {@link #onFinish(Object, Throwable, boolean)} exactly once
 * when the subscription completes, errors or is cancelled. For single result publishers the first value is treated as
 * successful completion, so a downstream cancellation after the value has been emitted is not reported as a
 * cancellation.
 *
 * @param <T> The element type
 */
abstract class InstrumentedSubscriber<T> implements CoreSubscriber<T>, Subscription, Scannable {

    private static final Logger LOG = LoggerFactory.getLogger(InstrumentedSubscriber.class);

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<InstrumentedSubscriber> FINISHED_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(InstrumentedSubscriber.class, "finished");

    private final CoreSubscriber<? super T> actual;
    private final boolean singleResult;
    private @Nullable Subscription upstream;
    private volatile int finished;

    /**
     * @param actual The downstream subscriber
     * @param singleResult Whether the publisher emits at most one value
     */
    InstrumentedSubscriber(CoreSubscriber<? super T> actual, boolean singleResult) {
        this.actual = actual;
        this.singleResult = singleResult;
    }

    /**
     * Instruments a {@link Mono}.
     *
     * @param source The source
     * @param factory The subscriber factory
     * @param <T> The element type
     * @return The instrumented mono
     */
    static <T> Mono<T> instrument(Mono<T> source, Factory factory) {
        return InstrumentedMono.create(source, factory);
    }

    /**
     * Instruments a {@link Flux}.
     *
     * @param source The source
     * @param factory The subscriber factory
     * @param <T> The element type
     * @return The instrumented flux
     */
    static <T> Flux<T> instrument(Flux<T> source, Factory factory) {
        return InstrumentedFlux.create(source, factory);
    }

    /**
     * Invoked when the publisher is subscribed.
     */
    protected abstract void onStart();

    /**
     * Invoked exactly once when the subscription terminates.
     *
     * @param value The value of a single result publisher, or {@code null}
     * @param error The error or {@code null} if the publisher completed successfully or was cancelled
     * @param cancelled Whether the subscription was cancelled before it completed
     */
    protected abstract void onFinish(@Nullable Object value, @Nullable Throwable error, boolean cancelled);

    @Override
    public final void onSubscribe(Subscription s) {
        if (Operators.validate(upstream, s)) {
            upstream = s;
            try {
                onStart();
            } catch (Exception e) {
                LOG.error("Error starting method metrics", e);
            }
            actual.onSubscribe(this);
        }
    }

    @Override
    public final void onNext(T t) {
        if (singleResult) {
            finish(t, null, false);
        }
        actual.onNext(t);
    }

    @Override
    public final void onError(Throwable t) {
        finish(null, t, false);
        actual.onError(t);
    }

    @Override
    public final void onComplete() {
        finish(null, null, false);
        actual.onComplete();
    }

    @Override
    public final void request(long n) {
        Subscription s = upstream;
        if (s != null) {
            s.request(n);
        }
    }

    @Override
    public final void cancel() {
        finish(null, null, true);
        Subscription s = upstream;
        if (s != null) {
            s.cancel();
        }
    }

    @Override
    public final Context currentContext() {
        return actual.currentContext();
    }

    @Override
    public final @Nullable Object scanUnsafe(Attr key) {
        if (key == Attr.PARENT) {
            return upstream;
        }
        if (key == Attr.ACTUAL) {
            return actual;
        }
        if (key == Attr.RUN_STYLE) {
            return Attr.RunStyle.SYNC;
        }
        return null;
    }

    private void finish(@Nullable Object value, @Nullable Throwable error, boolean cancelled) {
        if (FINISHED_UPDATER.compareAndSet(this, 0, 1)) {
            try {
                onFinish(value, error, cancelled);
            } catch (Exception e) {
                LOG.error("Error recording method metrics", e);
            }
        }
    }

    /**
     * Creates the subscriber for each subscription.
     */
    interface Factory {
        /**
         * @param actual The downstream subscriber
         * @param singleResult Whether the publisher emits at most one value
         * @param <T> The element type
         * @return The subscriber
         */
        <T> InstrumentedSubscriber<T> create(CoreSubscriber<? super T> actual, boolean singleResult);
    }

    private static final class InstrumentedMono<T> extends MonoOperator<T, T> {
        private final Factory factory;

        private InstrumentedMono(Mono<? extends T> source, Factory factory) {
            super(source);
            this.factory = factory;
        }

        static <T> Mono<T> create(Mono<T> source, Factory factory) {
            return onAssembly(new InstrumentedMono<>(source, factory));
        }

        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            source.subscribe(factory.create(actual, true));
        }
    }

    private static final class InstrumentedFlux<T> extends FluxOperator<T, T> {
        private final Factory factory;

        private InstrumentedFlux(Flux<? extends T> source, Factory factory) {
            super(source);
            this.factory = factory;
        }

        static <T> Flux<T> create(Flux<T> source, Factory factory) {
            return onAssembly(new InstrumentedFlux<>(source, factory));
        }

        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            source.subscribe(factory.create(actual, false));
        }
    }
}
