/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.configuration.metrics.binder.web;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a streaming response body to record metrics on terminal signals.
 *
 * @param <T> The body item type
 */
@Internal
final class StreamingWebMetricsBodyPublisher<T> extends Flux<T> {

    private final Publisher<T> publisher;
    private final HttpResponse<?> response;
    private final WebMetricsHelper webMetricsHelper;
    private final AtomicBoolean recorded = new AtomicBoolean();

    StreamingWebMetricsBodyPublisher(Publisher<T> publisher,
                                     HttpResponse<?> response,
                                     WebMetricsHelper webMetricsHelper) {
        this.publisher = Flux.from(publisher);
        this.response = response;
        this.webMetricsHelper = webMetricsHelper;
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
                        recordSuccess();
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
                recordError(throwable);
                actual.onError(throwable);
            }

            @Override
            public void onComplete() {
                recordSuccess();
                actual.onComplete();
            }
        });
    }

    private void recordSuccess() {
        if (recorded.compareAndSet(false, true)) {
            webMetricsHelper.onResponse(response);
        }
    }

    private void recordError(Throwable throwable) {
        if (recorded.compareAndSet(false, true)) {
            webMetricsHelper.error(response, throwable);
        }
    }
}
