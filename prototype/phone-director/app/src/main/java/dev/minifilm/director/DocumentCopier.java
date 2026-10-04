package dev.minifilm.director;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Local document copy. The caller owns any partial destination and the source is never deleted. */
public final class DocumentCopier implements AutoCloseable {
    public interface Listener {
        void onComplete(long bytes);
        void onError(String message);
    }

    interface StreamOpener {
        InputStream openInput(Uri source, CancellationSignal signal) throws IOException;
        OutputStream openOutput(Uri destination, CancellationSignal signal) throws IOException;
    }

    private static final int MAX_OPERATIONS = 4;
    private final Object lifecycle = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StreamOpener opener;
    // A new copy need not wait for a cancelled provider. A finite lifetime budget also counts
    // cancellation cleanup, so repeated uncooperative providers cannot spawn unlimited work.
    private final Semaphore available = new Semaphore(MAX_OPERATIONS);
    private final ThreadPoolExecutor workers = executor(MAX_OPERATIONS, "document-copy");
    private final ThreadPoolExecutor cleanup = executor(MAX_OPERATIONS * 3, "document-close");
    private Operation current;
    private boolean closed;

    private static final class Operation {
        final Uri source;
        final Uri destination;
        final long maxBytes;
        final Listener listener;
        final CancellationSignal signal = new CancellationSignal();
        InputStream input;
        OutputStream output;
        boolean cancelled;
        boolean slotHeld;
        boolean workerFinished;
        boolean signalQueued;
        boolean callbackPosted;
        boolean delivered;
        int cleanupTasks;

        Operation(Uri source, Uri destination, long maxBytes, Listener listener) {
            this.source = source;
            this.destination = destination;
            this.maxBytes = maxBytes;
            this.listener = listener;
        }
    }

    private static final class SizeLimitException extends IOException { }

    public DocumentCopier(Context context) {
        this(context, resolverOpener(context));
    }

    // Synthetic tests can block reads/writes/opens without touching a camera, microphone or network.
    DocumentCopier(Context context, StreamOpener opener) {
        Objects.requireNonNull(context);
        this.opener = Objects.requireNonNull(opener);
    }

    public void copy(Uri source, Uri destination, long maxBytes, Listener listener) {
        Objects.requireNonNull(listener);
        synchronized (lifecycle) {
            if (closed) return;
            cancelLocked(current);
            Operation operation = new Operation(source, destination, maxBytes, listener);
            current = operation; // Operation identity is the generation, including validation failures.
            if (!localDocument(source) || !localDocument(destination) || maxBytes <= 0
                    || source.equals(destination)) {
                finishLocked(operation, 0, "Choose different local source and destination files with a positive size limit.");
                return;
            }
            if (!available.tryAcquire()) {
                finishLocked(operation, 0, "A previous file provider is still finishing. Try saving again shortly.");
                return;
            }
            operation.slotHeld = true;
            workers.execute(() -> run(operation));
        }
    }

    public void cancel() {
        synchronized (lifecycle) {
            cancelLocked(current);
            current = null;
        }
    }

    @Override public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            cancelLocked(current);
            current = null;
            // All cancellation tasks are submitted while holding lifecycle before shutdown.
            // Late-open handles and worker-owned final closes run on their original worker.
            workers.shutdown();
            cleanup.shutdown();
        }
    }

    private void run(Operation operation) {
        long copied = 0;
        boolean complete = false;
        String error = "Could not copy the file. Keep the source and try a new destination.";
        try {
            if (!active(operation)) return;
            InputStream input = opener.openInput(operation.source, operation.signal);
            if (input == null) throw new IOException("No input stream");
            if (!registerInput(operation, input)) { input.close(); return; }
            // In particular, a source open that returns after cancellation never opens the destination.
            if (!active(operation)) return;
            OutputStream output = opener.openOutput(operation.destination, operation.signal);
            if (output == null) throw new IOException("No output stream");
            if (!registerOutput(operation, output)) { output.close(); return; }
            byte[] buffer = new byte[8192];
            while (active(operation)) {
                int count = input.read(buffer);
                if (!active(operation)) return;
                if (count < 0) break;
                if (count == 0) {
                    int value = input.read();
                    if (!active(operation)) return;
                    if (value < 0) break;
                    buffer[0] = (byte) value;
                    count = 1;
                }
                if (count > operation.maxBytes - copied) throw new SizeLimitException();
                output.write(buffer, 0, count);
                copied += count;
            }
            if (!active(operation)) return;
            output.flush();
            if (!active(operation)) return;
            closeOwned(operation); // Both close results belong to success, not just the last write.
            if (!active(operation)) return;
            complete = true;
        } catch (SizeLimitException failure) {
            error = "The file exceeds the save size limit. Choose a smaller file.";
        } catch (Exception failure) {
            // Provider exceptions can contain private URIs/text. Do not log or display them.
        } finally {
            try { closeOwned(operation); }
            catch (IOException failure) { complete = false; }
            synchronized (lifecycle) {
                operation.workerFinished = true;
                releaseIfFinishedLocked(operation);
                finishLocked(operation, copied, complete ? null : error);
            }
        }
    }

    private boolean active(Operation operation) {
        synchronized (lifecycle) { return current == operation && !closed && !operation.cancelled; }
    }

    private boolean registerInput(Operation operation, InputStream input) {
        synchronized (lifecycle) {
            if (current != operation || closed || operation.cancelled) return false;
            operation.input = input;
            return true;
        }
    }

    private boolean registerOutput(Operation operation, OutputStream output) {
        synchronized (lifecycle) {
            if (current != operation || closed || operation.cancelled) return false;
            operation.output = output;
            return true;
        }
    }

    private void closeOwned(Operation operation) throws IOException {
        OutputStream output;
        synchronized (lifecycle) {
            output = operation.output;
            operation.output = null; // Claim once: cancellation cannot double-close this resource.
        }
        IOException failure = closeStream(output);
        InputStream input;
        synchronized (lifecycle) {
            // Keep the input registered while output.close is in progress: cancellation can
            // independently close it even if the provider's output close itself blocks.
            input = operation.input;
            operation.input = null;
        }
        IOException inputFailure = closeStream(input);
        if (failure != null) throw failure;
        if (inputFailure != null) throw inputFailure;
    }

    private static IOException closeStream(Closeable stream) {
        if (stream == null) return null;
        try { stream.close(); return null; }
        catch (IOException failure) { return failure; }
        catch (RuntimeException failure) { return new IOException("Stream close failed", failure); }
    }

    private void cancelLocked(Operation operation) {
        if (operation == null || operation.cancelled) return;
        operation.cancelled = true; // Invalidate publication before scheduling any potentially blocking cleanup.
        if (operation.slotHeld && !operation.workerFinished && !operation.signalQueued) {
            operation.signalQueued = true;
            cleanupLocked(operation, operation.signal::cancel);
        }
        InputStream input = operation.input;
        OutputStream output = operation.output;
        operation.input = null;
        operation.output = null;
        // Independent tasks: one provider's close/cancel cannot serialize the other resource's close.
        if (input != null) cleanupLocked(operation, () -> closeStream(input));
        if (output != null) cleanupLocked(operation, () -> closeStream(output));
    }

    private void cleanupLocked(Operation operation, Runnable action) {
        operation.cleanupTasks++;
        cleanup.execute(() -> {
            try { action.run(); }
            catch (RuntimeException ignored) { }
            finally {
                synchronized (lifecycle) {
                    operation.cleanupTasks--;
                    releaseIfFinishedLocked(operation);
                }
            }
        });
    }

    private void releaseIfFinishedLocked(Operation operation) {
        if (operation.slotHeld && operation.workerFinished && operation.cleanupTasks == 0) {
            operation.slotHeld = false;
            available.release();
        }
    }

    private void finishLocked(Operation operation, long copied, String error) {
        if (current != operation || closed || operation.cancelled || operation.callbackPosted) return;
        operation.callbackPosted = true;
        main.post(() -> {
            synchronized (lifecycle) {
                if (current != operation || closed || operation.cancelled || operation.delivered) return;
                operation.delivered = true;
                current = null; // Delivery is claimed once; cancellation suppresses any still-queued callback.
            }
            if (error == null) operation.listener.onComplete(copied);
            else operation.listener.onError(error);
        });
    }

    private static boolean localDocument(Uri uri) {
        return uri != null && ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()));
    }

    private static ThreadPoolExecutor executor(int threads, String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threads), factory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static StreamOpener resolverOpener(Context context) {
        Context application = Objects.requireNonNull(context).getApplicationContext();
        ContentResolver resolver = (application == null ? context : application).getContentResolver();
        return new StreamOpener() {
            @Override public InputStream openInput(Uri uri, CancellationSignal signal) throws IOException {
                ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r", signal);
                if (descriptor == null) throw new IOException("No readable file descriptor");
                return new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
            }
            @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) throws IOException {
                ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "wt", signal);
                if (descriptor == null) throw new IOException("No writable file descriptor");
                return new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
            }
        };
    }
}
