/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.micrometer.observation.http.server;

import io.micrometer.observation.Observation;
import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a streaming response body to stop the observation on terminal signals.
 *
 * @param <T> The body item type
 */
@Internal
final class StreamingObservationBodyPublisher<T> extends Flux<T> {

    private final Publisher<T> publisher;
    private final Observation observation;
    private final AtomicBoolean stopped = new AtomicBoolean();

    StreamingObservationBodyPublisher(Publisher<T> publisher, Observation observation) {
        this.publisher = Flux.from(publisher);
        this.observation = observation;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
        publisher.subscribe(new CoreSubscriber<>() {
            @Override
            public Context currentContext() {
                return actual.currentContext();
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                actual.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscription.request(n);
                    }

                    @Override
                    public void cancel() {
                        stopObservation();
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(T item) {
                actual.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                if (stopped.compareAndSet(false, true)) {
                    observation.error(throwable);
                    observation.stop();
                }
                actual.onError(throwable);
            }

            @Override
            public void onComplete() {
                stopObservation();
                actual.onComplete();
            }
        });
    }

    private void stopObservation() {
        if (stopped.compareAndSet(false, true)) {
            observation.stop();
        }
    }
}
