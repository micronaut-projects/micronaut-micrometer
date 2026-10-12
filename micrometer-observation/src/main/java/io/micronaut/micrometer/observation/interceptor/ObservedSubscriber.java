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
package io.micronaut.micrometer.observation.interceptor;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.micrometer.observation.ObservationPropagationContext;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscription;
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
 * Observes a single subscription of a reactive result: the observation is started when the publisher is subscribed
 * and stopped exactly once when it completes, errors or is cancelled. For single result publishers the first value
 * completes the observation, so a downstream cancellation after the value has been emitted is not a cancellation.
 *
 * @param <T> The element type
 */
final class ObservedSubscriber<T> implements CoreSubscriber<T>, Subscription, Scannable {

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<ObservedSubscriber> STOPPED =
        AtomicIntegerFieldUpdater.newUpdater(ObservedSubscriber.class, "stopped");

    private final CoreSubscriber<? super T> actual;
    private final boolean singleResult;
    private final Observation observation;
    private final Context context;
    private @Nullable Subscription upstream;
    private volatile int stopped;

    private ObservedSubscriber(CoreSubscriber<? super T> actual, boolean singleResult, Observation observation) {
        this.actual = actual;
        this.singleResult = singleResult;
        this.observation = observation;
        Context actualContext = actual.currentContext();
        this.context = observation.isNoop()
            ? actualContext
            : ReactorPropagation.addContextElement(actualContext, new ObservationPropagationContext(observation));
    }

    /**
     * Observes each subscription of a {@link Mono}.
     *
     * @param source The source
     * @param observedMethod The observed method
     * @param registry The registry
     * @param <T> The element type
     * @return The observed mono
     */
    static <T> Mono<T> observe(Mono<T> source, ObservedInterceptor.ObservedMethod observedMethod, ObservationRegistry registry) {
        return ObservedMono.create(source, observedMethod, registry);
    }

    /**
     * Observes each subscription of a {@link Flux}.
     *
     * @param source The source
     * @param observedMethod The observed method
     * @param registry The registry
     * @param <T> The element type
     * @return The observed flux
     */
    static <T> Flux<T> observe(Flux<T> source, ObservedInterceptor.ObservedMethod observedMethod, ObservationRegistry registry) {
        return ObservedFlux.create(source, observedMethod, registry);
    }

    /**
     * Starts the observation of a subscription.
     *
     * @param actual The downstream subscriber
     * @param singleResult Whether the publisher emits at most one value
     * @param observedMethod The observed method
     * @param registry The registry
     * @param <T> The element type
     * @return The subscriber
     */
    private static <T> ObservedSubscriber<T> start(CoreSubscriber<? super T> actual,
                                                   boolean singleResult,
                                                   ObservedInterceptor.ObservedMethod observedMethod,
                                                   ObservationRegistry registry) {
        return new ObservedSubscriber<>(actual, singleResult, observedMethod.start(registry));
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (Operators.validate(upstream, s)) {
            upstream = s;
            actual.onSubscribe(this);
        }
    }

    @Override
    public void onNext(T t) {
        if (singleResult) {
            stop(null);
        }
        actual.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        stop(t);
        actual.onError(t);
    }

    @Override
    public void onComplete() {
        stop(null);
        actual.onComplete();
    }

    @Override
    public void request(long n) {
        Subscription s = upstream;
        if (s != null) {
            s.request(n);
        }
    }

    @Override
    public void cancel() {
        stop(null);
        Subscription s = upstream;
        if (s != null) {
            s.cancel();
        }
    }

    @Override
    public Context currentContext() {
        return context;
    }

    @Override
    public @Nullable Object scanUnsafe(Attr key) {
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

    private void stop(@Nullable Throwable error) {
        if (STOPPED.compareAndSet(this, 0, 1)) {
            if (error != null) {
                observation.error(ObservedInterceptor.unwrap(error));
            }
            observation.stop();
        }
    }

    private static final class ObservedMono<T> extends MonoOperator<T, T> {
        private final ObservedInterceptor.ObservedMethod observedMethod;
        private final ObservationRegistry registry;

        private ObservedMono(Mono<? extends T> source, ObservedInterceptor.ObservedMethod observedMethod, ObservationRegistry registry) {
            super(source);
            this.observedMethod = observedMethod;
            this.registry = registry;
        }

        static <T> Mono<T> create(Mono<T> source, ObservedInterceptor.ObservedMethod observedMethod, ObservationRegistry registry) {
            return onAssembly(new ObservedMono<>(source, observedMethod, registry));
        }

        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            ObservedSubscriber<T> subscriber = start(actual, true, observedMethod, registry);
            // subscription time work, for example a deferred supplier, runs in the scope of the observation
            try (Observation.Scope ignore = subscriber.observation.openScope()) {
                source.subscribe(subscriber);
            }
        }
    }

    private static final class ObservedFlux<T> extends FluxOperator<T, T> {
        private final ObservedInterceptor.ObservedMethod observedMethod;
        private final ObservationRegistry registry;

        private ObservedFlux(Flux<? extends T> source, ObservedInterceptor.ObservedMethod observedMethod, ObservationRegistry registry) {
            super(source);
            this.observedMethod = observedMethod;
            this.registry = registry;
        }

        static <T> Flux<T> create(Flux<T> source, ObservedInterceptor.ObservedMethod observedMethod, ObservationRegistry registry) {
            return onAssembly(new ObservedFlux<>(source, observedMethod, registry));
        }

        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            ObservedSubscriber<T> subscriber = start(actual, false, observedMethod, registry);
            // subscription time work, for example a deferred supplier, runs in the scope of the observation
            try (Observation.Scope ignore = subscriber.observation.openScope()) {
                source.subscribe(subscriber);
            }
        }
    }
}
