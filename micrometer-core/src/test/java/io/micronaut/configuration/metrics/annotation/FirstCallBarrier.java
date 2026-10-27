package io.micronaut.configuration.metrics.annotation;

import jakarta.inject.Singleton;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * Called by the annotation expressions of the targets: a thread that armed a barrier waits there
 * once, while its interceptor resolves the annotations of a method it has not cached yet.
 */
@Singleton
public class FirstCallBarrier {

    private static final ThreadLocal<CyclicBarrier> BARRIER = new ThreadLocal<>();

    public static void arm(CyclicBarrier barrier) {
        BARRIER.set(barrier);
    }

    public String arrive(String value) {
        CyclicBarrier barrier = BARRIER.get();
        if (barrier != null) {
            BARRIER.remove();
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("The other first invocation did not arrive", e);
            }
        }
        return value;
    }
}
