package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Headless synthetic frames only; no Activity, live camera, microphone or playback. */
@RunWith(AndroidJUnit4.class)
public final class ReferenceFrameDecoderTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 30_000) public void syntheticSeekReturnsBoundedOwnedFrameAndExactDurationWithoutChangingSource() throws Exception {
        File fixture = fixture(); byte[] before = digest(fixture);
        long duration = duration(fixture);
        ReferenceFrameDecoder decoder = new ReferenceFrameDecoder(context); Outcome result = new Outcome();
        try {
            decoder.decode(Uri.fromFile(fixture), 1500, result);
            assertTrue("Synthetic reference seek timed out", result.done.await(15, TimeUnit.SECONDS));
            assertNull(result.error.get(), result.error.get()); assertNotNull(result.frame.get());
            Bitmap frame = result.frame.get();
            assertFalse(frame.isRecycled());
            assertTrue("Frame width must be bounded", frame.getWidth() > 0 && frame.getWidth() <= 512);
            assertTrue("Frame height must be bounded", frame.getHeight() > 0 && frame.getHeight() <= 512);
            assertEquals("Timestamp is requested seek, not a claimed exact decoded PTS", 1500, result.requested);
            assertEquals(duration, result.duration);
            decoder.close();
            assertFalse("Successfully delivered frame belongs to receiver after decoder closes", frame.isRecycled());
            assertArrayEquals("Read-only seek must retain source bytes", before, digest(fixture));
        } finally { decoder.close(); if (result.frame.get() != null && !result.frame.get().isRecycled()) result.frame.get().recycle(); }
    }

    @Test(timeout = 30_000) public void negativeAtEndAndRemoteRequestsRejectWithoutProducingFrames() throws Exception {
        File fixture = fixture(); long duration = duration(fixture); byte[] before = digest(fixture);
        for (int i = 0; i < 3; i++) {
            Uri source = i == 2 ? Uri.parse("https://example.invalid/synthetic-reference.mp4") : Uri.fromFile(fixture);
            long time = i == 0 ? -1 : i == 1 ? duration : 1500;
            ReferenceFrameDecoder decoder = new ReferenceFrameDecoder(context); Outcome result = new Outcome();
            try {
                decoder.decode(source, time, result);
                assertTrue("Invalid synthetic request did not return", result.done.await(8, TimeUnit.SECONDS));
                assertNull("Invalid seek/source must not return a frame", result.frame.get());
                assertNotNull("Invalid seek/source needs an actionable error", result.error.get());
                assertFalse(result.error.get().trim().isEmpty());
            } finally { decoder.close(); if (result.frame.get() != null) result.frame.get().recycle(); }
        }
        assertArrayEquals(before, digest(fixture));
    }

    @Test(timeout = 30_000) public void closeAfterDecodeBeforeMainDeliverySuppressesPendingCallback() throws Exception {
        ReferenceFrameDecoder decoder = new ReferenceFrameDecoder(context);
        CountDownLatch mainHeld = new CountDownLatch(1), unblockMain = new CountDownLatch(1), drained = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger(); AtomicReference<Bitmap> unexpected = new AtomicReference<>();
        AtomicReference<String> barrierError = new AtomicReference<>();
        // Briefly hold delivery only, without opening an Activity or changing device state.
        // Reflection accesses the executor solely to establish a deterministic decode-complete barrier.
        main.post(() -> {
            mainHeld.countDown();
            try {
                if (!unblockMain.await(5, TimeUnit.SECONDS)) barrierError.set("Main delivery barrier timed out");
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); barrierError.set("Main delivery barrier interrupted"); }
        });
        try {
            assertTrue(mainHeld.await(3, TimeUnit.SECONDS));
            decoder.decode(Uri.fromFile(fixture()), 1500, new ReferenceFrameDecoder.Listener() {
                public void onFrame(Bitmap frame, long requestedMs, long durationMs) { callbacks.incrementAndGet(); unexpected.set(frame); }
                public void onError(String message) { callbacks.incrementAndGet(); }
            });
            Field workerField = ReferenceFrameDecoder.class.getDeclaredField("worker"); workerField.setAccessible(true);
            ExecutorService worker = (ExecutorService) workerField.get(decoder);
            worker.submit(() -> { }).get(3, TimeUnit.SECONDS);
            decoder.close();
            unblockMain.countDown(); main.post(drained::countDown);
            assertTrue("Pending delivery queue did not drain", drained.await(5, TimeUnit.SECONDS));
            assertNull(barrierError.get(), barrierError.get());
            assertEquals("Closed decoder must suppress both frame/error callbacks", 0, callbacks.get());
            // Bitmap recycling of this suppressed result is a source-reviewed branch;
            // its unpublished bitmap is deliberately not exposed by the public API.
        } finally {
            decoder.close(); unblockMain.countDown();
            if (unexpected.get() != null && !unexpected.get().isRecycled()) unexpected.get().recycle();
        }
    }

    private File fixture() {
        File file = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Root must install the labelled synthetic spoken fixture", file.isFile()); return file;
    }
    private static long duration(File file) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try { metadata.setDataSource(file.getAbsolutePath()); return Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { metadata.release(); }
    }
    private static byte[] digest(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream source = new FileInputStream(file)) {
            byte[] block = new byte[8192]; int count; while ((count = source.read(block)) != -1) hash.update(block, 0, count);
        }
        return hash.digest();
    }
    private static final class Outcome implements ReferenceFrameDecoder.Listener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Bitmap> frame = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
        long requested, duration;
        public void onFrame(Bitmap value, long requestedMs, long durationMs) {
            frame.set(value); requested = requestedMs; duration = durationMs; done.countDown();
        }
        public void onError(String message) { error.set(message); done.countDown(); }
    }
}
