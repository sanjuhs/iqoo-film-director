package dev.minifilm.director;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Shared ownership of one local core/context lifetime, including an idle planner handle. */
final class LocalModelLease {
    private static final Semaphore PERMIT = new Semaphore(1, true);
    private static volatile String owner = "";
    private LocalModelLease() { }

    static Token acquire(String label, BooleanSupplier cancelled, long timeoutMs)
            throws InterruptedException, TimeoutException {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (true) {
            if (cancelled.getAsBoolean()) throw new CancellationException("Local model request cancelled");
            long remaining = end - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("Another local model is still releasing resources");
            if (!PERMIT.tryAcquire(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS)) continue;
            if (cancelled.getAsBoolean()) {
                PERMIT.release();
                throw new CancellationException("Local model request cancelled");
            }
            owner = label;
            return new Token();
        }
    }
    static boolean isHeld() { return PERMIT.availablePermits() == 0; }
    static String ownerForDiagnostics() { return owner; }

    static final class Token implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();
        /** Call only after nativeFree has returned; closing a request is not resource release. */
        @Override public void close() {
            if (released.compareAndSet(false, true)) {
                owner = "";
                PERMIT.release();
            }
        }
    }
}
