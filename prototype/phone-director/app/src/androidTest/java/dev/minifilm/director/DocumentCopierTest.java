package dev.minifilm.director;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Synthetic local copy/provider regressions. No activity, media capture, playback or external provider. */
@RunWith(AndroidJUnit4.class)
public final class DocumentCopierTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 30_000) public void realLocalFileToAppContentDestinationIsExactAndSuccessWaitsForFlushAndBothCloses() throws Exception {
        try (Fixture fixture = new Fixture(context)) {
            DocumentCopier normal = new DocumentCopier(context);
            try {
                Outcome actual = new Outcome(); ui(() -> normal.copy(fixture.sourceUri(), fixture.content(fixture.destination), 100000, actual));
                actual.success(); assertEquals(fixture.bytes.length, actual.bytes.get());
                assertArrayEquals(fixture.bytes, Files.readAllBytes(fixture.destination.toPath())); fixture.assertOriginal();
            } finally { ui(normal::close); }
            AtomicReference<TrackedInput> input = new AtomicReference<>(); AtomicReference<TrackedOutput> output = new AtomicReference<>();
            DocumentCopier measured = new DocumentCopier(context, new DocumentCopier.StreamOpener() {
                @Override public InputStream openInput(Uri uri, CancellationSignal signal) throws IOException {
                    TrackedInput value = new TrackedInput(new ParcelFileDescriptor.AutoCloseInputStream(
                            context.getContentResolver().openFileDescriptor(uri, "r", signal)), 17); input.set(value); return value;
                }
                @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) throws IOException {
                    TrackedOutput value = new TrackedOutput(new ParcelFileDescriptor.AutoCloseOutputStream(
                            context.getContentResolver().openFileDescriptor(uri, "wt", signal))); output.set(value); return value;
                }
            });
            try {
                Outcome result = new Outcome() {
                    @Override public void onComplete(long bytes) {
                        successSawClosed.set(input.get() != null && input.get().closes.get() == 1 && output.get() != null
                                && output.get().flushes.get() > 0 && output.get().closes.get() == 1); super.onComplete(bytes);
                    }
                };
                ui(() -> measured.copy(fixture.sourceUri(), fixture.content(fixture.secondDestination), 100000, result));
                result.success(); assertTrue("Completion must wait for explicit flush and both closes", result.successSawClosed.get());
                assertTrue("Short provider reads must still copy the complete file", input.get().reads.get() > 1);
                assertArrayEquals(fixture.bytes, Files.readAllBytes(fixture.secondDestination.toPath())); fixture.assertOriginal();
            } finally { ui(measured::close); }
        }
    }

    @Test(timeout = 20_000) public void maxByteFailureDoesNotWriteBeyondLimitOrAlterSourceAndClosesOpenedStreams() throws Exception {
        try (Fixture fixture = new Fixture(context)) {
            AtomicReference<TrackedInput> input = new AtomicReference<>(); AtomicInteger outputs = new AtomicInteger();
            ByteArrayOutputStream saved = new ByteArrayOutputStream(); TrackedOutput output = new TrackedOutput(saved);
            DocumentCopier copier = new DocumentCopier(context, new DocumentCopier.StreamOpener() {
                @Override public InputStream openInput(Uri uri, CancellationSignal signal) throws IOException {
                    TrackedInput stream = new TrackedInput(new FileInputStream(fixture.source), 17); input.set(stream); return stream;
                }
                @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) { outputs.incrementAndGet(); return output; }
            });
            try {
                Outcome result = new Outcome(); ui(() -> copier.copy(fixture.sourceUri(), fixture.content(fixture.destination), 64, result));
                result.failure(); assertTrue("Oversize copy must not exceed the explicit byte budget", saved.size() <= 64);
                assertEquals(1, input.get().closes.get()); if (outputs.get() > 0) assertEquals(1, output.closes.get()); fixture.assertOriginal();
            } finally { ui(copier::close); }
        }
    }

    @Test(timeout = 30_000) public void cancelClosesOutputThatBlocksInWriteSuppressesCallbacksAndNextCopyCompletesIndependently() throws Exception {
        Uri first = local("write-blocked"), next = local("write-next"); BlockingOutput blocked = new BlockingOutput();
        TrackedInput oldInput = new TrackedInput(new ByteArrayInputStream(data(4096)), 4096);
        ByteArrayOutputStream nextBytes = new ByteArrayOutputStream(); TrackedOutput nextOutput = new TrackedOutput(nextBytes);
        byte[] expected = data(333); AtomicInteger oldCallbacks = new AtomicInteger();
        DocumentCopier copier = new DocumentCopier(context, new DocumentCopier.StreamOpener() {
            @Override public InputStream openInput(Uri uri, CancellationSignal signal) { return uri.equals(first) ? oldInput : new TrackedInput(new ByteArrayInputStream(expected), 11); }
            @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) { return uri.getPath().startsWith("/write-blocked") ? blocked : nextOutput; }
        });
        try {
            ui(() -> copier.copy(first, first.buildUpon().appendPath("out").build(), 10000, counting(oldCallbacks)));
            assertTrue(blocked.entered.await(5, TimeUnit.SECONDS)); Outcome result = new Outcome();
            ui(() -> { copier.cancel(); copier.copy(next, next.buildUpon().appendPath("out").build(), 10000, result); });
            result.success(); assertArrayEquals(expected, nextBytes.toByteArray());
            assertTrue("Only output.close may release the blocked write", blocked.closed.await(5, TimeUnit.SECONDS));
            assertTrue(oldInput.closed.await(5, TimeUnit.SECONDS)); drainMain();
            assertEquals("Cancel must suppress completion and errors of old generation", 0, oldCallbacks.get());
            assertEquals(1, blocked.closes.get()); assertEquals(1, oldInput.closes.get());
            assertEquals("Provider close must not run on main", Boolean.FALSE, blocked.closedOnMain.get());
        } finally { ui(copier::close); blocked.close(); }
    }

    @Test(timeout = 30_000) public void sourceOpenIgnoringCancellationReturnsLateClosedInputWithoutOpeningOldOutput() throws Exception {
        Uri first = local("late-open"), next = local("late-next");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), cancelled = new CountDownLatch(1);
        TrackedInput late = new TrackedInput(new ByteArrayInputStream(data(100)), 10); AtomicInteger oldOutputs = new AtomicInteger(), oldCallbacks = new AtomicInteger();
        byte[] expected = data(501); ByteArrayOutputStream saved = new ByteArrayOutputStream();
        DocumentCopier copier = new DocumentCopier(context, new DocumentCopier.StreamOpener() {
            @Override public InputStream openInput(Uri uri, CancellationSignal signal) throws IOException {
                if (!uri.equals(first)) return new TrackedInput(new ByteArrayInputStream(expected), 9);
                signal.setOnCancelListener(cancelled::countDown); entered.countDown();
                awaitIgnoringInterrupts(release, 20); return late; // Deliberately ignores cancellation, like a stalled provider.
            }
            @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) {
                if (uri.getPath().startsWith("/late-open")) { oldOutputs.incrementAndGet(); return new ByteArrayOutputStream(); }
                return new TrackedOutput(saved);
            }
        });
        try {
            ui(() -> copier.copy(first, first.buildUpon().appendPath("out").build(), 10000, counting(oldCallbacks)));
            assertTrue(entered.await(5, TimeUnit.SECONDS)); Outcome result = new Outcome();
            ui(() -> { copier.cancel(); copier.copy(next, next.buildUpon().appendPath("out").build(), 10000, result); });
            result.success(); assertArrayEquals(expected, saved.toByteArray());
            assertEquals("New copy must finish while stale open remains blocked", 1L, release.getCount());
            assertTrue("CancellationSignal must also be cancelled", cancelled.await(5, TimeUnit.SECONDS));
            release.countDown(); assertTrue("Late returned input must be closed", late.closed.await(5, TimeUnit.SECONDS)); drainMain();
            assertEquals(1, late.closes.get()); assertEquals(0, late.reads.get()); assertEquals(0, oldOutputs.get()); assertEquals(0, oldCallbacks.get());
        } finally { release.countDown(); ui(copier::close); }
    }

    @Test(timeout = 20_000) public void revokedOrFailedDestinationNeverChangesOriginalAndClosesSource() throws Exception {
        try (Fixture fixture = new Fixture(context)) {
            for (boolean revoked : new boolean[] {true, false}) {
                AtomicReference<TrackedInput> input = new AtomicReference<>();
                DocumentCopier copier = new DocumentCopier(context, new DocumentCopier.StreamOpener() {
                    @Override public InputStream openInput(Uri uri, CancellationSignal signal) throws IOException {
                        TrackedInput stream = new TrackedInput(new FileInputStream(fixture.source), 13); input.set(stream); return stream;
                    }
                    @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) throws IOException {
                        if (revoked) throw new SecurityException("Synthetic destination grant revoked");
                        throw new IOException("Synthetic destination unavailable");
                    }
                });
                try {
                    Outcome result = new Outcome(); ui(() -> copier.copy(fixture.sourceUri(), fixture.content(fixture.destination), 100000, result));
                    result.failure(); assertNotNull(input.get()); assertTrue(input.get().closed.await(3, TimeUnit.SECONDS));
                    assertEquals(1, input.get().closes.get()); fixture.assertOriginal();
                } finally { ui(copier::close); }
            }
        }
    }

    @Test(timeout = 30_000) public void flushOrEitherCloseFailureCannotPublishSuccessAfterAllBytesWereWritten() throws Exception {
        try (Fixture fixture = new Fixture(context)) {
            for (String failurePoint : new String[] {"flush", "output-close", "input-close"}) {
                AtomicReference<TrackedInput> input = new AtomicReference<>(); ByteArrayOutputStream written = new ByteArrayOutputStream();
                TrackedOutput output = new TrackedOutput(written) {
                    @Override public void flush() throws IOException {
                        super.flush(); if (failurePoint.equals("flush")) throw new IOException("Synthetic flush failed");
                    }
                    @Override public void close() throws IOException {
                        super.close(); if (failurePoint.equals("output-close")) throw new IOException("Synthetic output close failed");
                    }
                };
                DocumentCopier copier = new DocumentCopier(context, new DocumentCopier.StreamOpener() {
                    @Override public InputStream openInput(Uri uri, CancellationSignal signal) throws IOException {
                        TrackedInput value = new TrackedInput(new FileInputStream(fixture.source), 83) {
                            @Override public void close() throws IOException {
                                super.close(); if (failurePoint.equals("input-close")) throw new IOException("Synthetic input close failed");
                            }
                        }; input.set(value); return value;
                    }
                    @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) { return output; }
                });
                try {
                    Outcome result = new Outcome(); ui(() -> copier.copy(fixture.sourceUri(), fixture.content(fixture.destination), 100000, result));
                    result.failure(); assertArrayEquals(fixture.bytes, written.toByteArray());
                    assertEquals(1, output.closes.get()); assertEquals(1, input.get().closes.get()); fixture.assertOriginal();
                } finally { ui(copier::close); }
            }
        }
    }

    @Test(timeout = 15_000) public void closeSuppressesQueuedValidationErrorAndRejectsNewCopiesWithoutOpeningStreams() throws Exception {
        AtomicInteger callbacks = new AtomicInteger(), opens = new AtomicInteger(); CountDownLatch drained = new CountDownLatch(1);
        DocumentCopier copier = new DocumentCopier(context, new DocumentCopier.StreamOpener() {
            @Override public InputStream openInput(Uri uri, CancellationSignal signal) { opens.incrementAndGet(); return new ByteArrayInputStream(data(10)); }
            @Override public OutputStream openOutput(Uri uri, CancellationSignal signal) { opens.incrementAndGet(); return new ByteArrayOutputStream(); }
        });
        ui(() -> {
            copier.copy(Uri.parse("https://example.invalid/synthetic"), local("invalid-out"), 100, counting(callbacks));
            copier.close(); copier.close();
            copier.copy(local("closed-in"), local("closed-out"), 100, counting(callbacks));
            new Handler(Looper.getMainLooper()).post(drained::countDown);
        });
        assertTrue(drained.await(5, TimeUnit.SECONDS)); drainMain(); assertEquals(0, callbacks.get()); assertEquals(0, opens.get());
    }

    private static class Outcome implements DocumentCopier.Listener {
        final CountDownLatch done = new CountDownLatch(1); final AtomicLong bytes = new AtomicLong(-1);
        final AtomicReference<String> error = new AtomicReference<>(); final AtomicInteger callbacks = new AtomicInteger();
        final AtomicBoolean main = new AtomicBoolean(true), successSawClosed = new AtomicBoolean(false);
        @Override public void onComplete(long value) { observe(); bytes.set(value); done.countDown(); }
        @Override public void onError(String message) { observe(); error.set(message); done.countDown(); }
        private void observe() { callbacks.incrementAndGet(); if (Looper.myLooper() != Looper.getMainLooper()) main.set(false); }
        void success() throws Exception { await(); assertNull(error.get()); assertTrue(bytes.get() >= 0); }
        void failure() throws Exception { await(); assertNotNull(error.get()); assertFalse(error.get().trim().isEmpty()); assertEquals(-1L, bytes.get()); }
        private void await() throws Exception { assertTrue("Local synthetic copy callback timed out", done.await(10, TimeUnit.SECONDS)); drainMain(); assertTrue(main.get()); assertEquals(1, callbacks.get()); }
    }
    private static DocumentCopier.Listener counting(AtomicInteger count) { return new DocumentCopier.Listener() {
        @Override public void onComplete(long bytes) { count.incrementAndGet(); } @Override public void onError(String message) { count.incrementAndGet(); }
    }; }
    private static class TrackedInput extends InputStream {
        final InputStream delegate; final int chunk; final AtomicInteger reads = new AtomicInteger(), closes = new AtomicInteger(); final CountDownLatch closed = new CountDownLatch(1);
        TrackedInput(InputStream delegate, int chunk) { this.delegate = delegate; this.chunk = chunk; }
        @Override public int read() throws IOException { reads.incrementAndGet(); return delegate.read(); }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { reads.incrementAndGet(); return delegate.read(bytes, offset, Math.min(length, chunk)); }
        @Override public void close() throws IOException { closes.incrementAndGet(); try { delegate.close(); } finally { closed.countDown(); } }
    }
    private static class TrackedOutput extends OutputStream {
        final OutputStream delegate; final AtomicInteger flushes = new AtomicInteger(), closes = new AtomicInteger();
        TrackedOutput(OutputStream delegate) { this.delegate = delegate; }
        @Override public void write(int value) throws IOException { delegate.write(value); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException { delegate.write(bytes, offset, length); }
        @Override public void flush() throws IOException { flushes.incrementAndGet(); delegate.flush(); }
        @Override public void close() throws IOException { closes.incrementAndGet(); delegate.close(); }
    }
    private static final class BlockingOutput extends OutputStream {
        final CountDownLatch entered = new CountDownLatch(1), closed = new CountDownLatch(1); final AtomicInteger closes = new AtomicInteger();
        final AtomicReference<Boolean> closedOnMain = new AtomicReference<>(); final AtomicBoolean physicallyClosed = new AtomicBoolean();
        @Override public void write(int value) throws IOException { write(new byte[] {(byte) value}, 0, 1); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            entered.countDown(); awaitIgnoringInterrupts(closed, 20); throw new IOException("Synthetic write released by close");
        }
        @Override public void close() {
            if (physicallyClosed.compareAndSet(false, true)) { closes.incrementAndGet(); closedOnMain.set(Looper.myLooper() == Looper.getMainLooper()); closed.countDown(); }
        }
    }
    private static final class Fixture implements AutoCloseable {
        final File directory, source, destination, secondDestination; final byte[] bytes = data(32771); final long modified; final Context context;
        Fixture(Context context) throws IOException {
            this.context = context; directory = new File(context.getFilesDir(), "synthetic-document-copy-" + UUID.randomUUID());
            if (!directory.mkdir()) throw new IOException("Synthetic fixture directory unavailable");
            source = new File(directory, "source.bin"); destination = new File(directory, "destination.bin"); secondDestination = new File(directory, "tracked-destination.bin");
            try (FileOutputStream output = new FileOutputStream(source)) { output.write(bytes); } modified = source.lastModified();
        }
        Uri sourceUri() { return Uri.fromFile(source); }
        Uri content(File file) { return FileProvider.getUriForFile(context, context.getPackageName() + ".files", file); }
        void assertOriginal() throws IOException { assertTrue(source.isFile()); assertEquals(bytes.length, source.length()); assertEquals(modified, source.lastModified()); assertArrayEquals(bytes, Files.readAllBytes(source.toPath())); }
        @Override public void close() { destination.delete(); secondDestination.delete(); source.delete(); directory.delete(); } // Only exact files this fixture created.
    }
    private static Uri local(String path) { return Uri.parse("content://synthetic.document-copier/" + path); }
    private static byte[] data(int length) { byte[] value = new byte[length]; for (int i = 0; i < length; i++) value[i] = (byte) ((i * 37 + 11) & 255); return value; }
    private static void ui(Runnable runnable) { InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable); }
    private static void drainMain() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private static void awaitIgnoringInterrupts(CountDownLatch latch, int seconds) throws IOException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (latch.getCount() != 0 && System.nanoTime() < end) try { if (latch.await(50, TimeUnit.MILLISECONDS)) return; } catch (InterruptedException ignored) { }
        if (latch.getCount() != 0) throw new IOException("Synthetic blocked-provider timeout");
    }
}
