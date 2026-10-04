package dev.minifilm.director;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real existing labelled synthetic clips, same-reader callback chaining. No microphone,
 * Activity, playback, preferences, model download or new production/native test hooks. */
@RunWith(AndroidJUnit4.class)
public final class TranscriberCompletionTest {
    private static final String TAG = "MiniFilmASRCompletion";

    @Test(timeout = 180_000) public void actualTranscriptionCallbackIsReleasedBeforeImmediateSameReaderTrim() throws Exception {
        assertNoCaptureOrNetwork();
        Source first = new Source("jacket-speech.mp4"), padded = new Source("jacket-speech-padded.mp4");
        ClipTranscriber reader = new ClipTranscriber(context());
        assertTrue("Existing verified tiny.en model is required", reader.isModelAvailable());
        int baseline = requestCount();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        try {
            ui(() -> reader.transcribe(first.uri, new ClipTranscriber.Listener() {
                @Override public void onComplete(List<SubtitleCue> cues, long elapsedMs) {
                    try {
                        assertReleased(reader, baseline);
                        assertKnownBoundedWords(cues, first.durationMs);
                        callbacks.incrementAndGet();
                        Log.i(TAG, "TRANSCRIBE_CALLBACK_RELEASED backend=CPU elapsedMs=" + elapsedMs + " segments=" + cues.size());
                        // This exact public call used to race the previous request's finally/busy reset.
                        reader.analyzeForTrim(padded.uri, 0, padded.durationMs, new ClipTranscriber.TrimListener() {
                            @Override public void onComplete(SpeechTrim trim, long trimElapsedMs) {
                                try {
                                    assertReleased(reader, baseline);
                                    assertNotNull(trim);
                                    assertEquals(padded.uri, trim.uri);
                                    assertEquals(padded.durationMs, trim.sourceDurationMs);
                                    assertTrue("Existing padded fixture should still yield a reviewed suggestion", trim.hasSuggestion);
                                    assertTrue(trim.reviewRequired);
                                    assertTrue(trim.suggestedInMs >= 0 && trim.suggestedOutMs <= padded.durationMs
                                            && trim.suggestedOutMs > trim.suggestedInMs);
                                    callbacks.incrementAndGet();
                                    Log.i(TAG, "IMMEDIATE_SAME_READER_TRIM_OK backend=CPU elapsedMs=" + trimElapsedMs
                                            + " requestReleased=true busy=false microphone=false playback=false");
                                } catch (Throwable error) { failure.set(error); }
                                finally { done.countDown(); }
                            }
                            @Override public void onError(String message) { failCallback(failure, done, message); }
                        });
                    } catch (Throwable error) { failure.set(error); done.countDown(); }
                }
                @Override public void onError(String message) { failCallback(failure, done, message); }
            }));
            assertTrue("Two actual local jobs must finish within the bound", done.await(150, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Same-reader transcription/trim completion failed", failure.get());
            assertEquals(2, callbacks.get());
            first.assertUnchanged(); padded.assertUnchanged();
        } finally { reader.close(); assertTrue(worker(reader).awaitTermination(10, TimeUnit.SECONDS)); }
        assertEquals(baseline, requestCount());
        assertNoCaptureOrNetwork();
    }

    @Test(timeout = 100_000) public void decoderErrorCallbackIsReleasedBeforeImmediateSameReaderActualTranscription() throws Exception {
        assertNoCaptureOrNetwork();
        Source source = new Source("jacket-speech.mp4");
        File missing = new File(context().getCacheDir(), "completion-test-local-source-never-created.mp4");
        assertFalse(missing.exists());
        ClipTranscriber reader = new ClipTranscriber(context());
        assertTrue("Existing verified tiny.en model is required", reader.isModelAvailable());
        int baseline = requestCount();
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        try {
            ui(() -> reader.transcribe(Uri.fromFile(missing), new ClipTranscriber.Listener() {
                @Override public void onComplete(List<SubtitleCue> cues, long elapsedMs) { failCallback(failure, done, "Missing source unexpectedly succeeded"); }
                @Override public void onError(String message) {
                    try {
                        assertReleased(reader, baseline);
                        assertNotNull(message);
                        assertFalse(message.contains(missing.getName()));
                        assertFalse(message.contains("file:"));
                        callbacks.incrementAndGet();
                        reader.transcribe(source.uri, new ClipTranscriber.Listener() {
                            @Override public void onComplete(List<SubtitleCue> cues, long elapsedMs) {
                                try {
                                    assertReleased(reader, baseline);
                                    assertKnownBoundedWords(cues, source.durationMs);
                                    callbacks.incrementAndGet();
                                    Log.i(TAG, "ERROR_THEN_IMMEDIATE_SAME_READER_ASR_OK backend=CPU elapsedMs=" + elapsedMs
                                            + " segments=" + cues.size() + " requestReleased=true busy=false safeError=true");
                                } catch (Throwable error) { failure.set(error); }
                                finally { done.countDown(); }
                            }
                            @Override public void onError(String error) { failCallback(failure, done, error); }
                        });
                    } catch (Throwable error) { failure.set(error); done.countDown(); }
                }
            }));
            assertTrue(done.await(80, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Same-reader error/transcription completion failed", failure.get());
            assertEquals(2, callbacks.get());
            source.assertUnchanged();
            assertFalse(missing.exists());
        } finally { reader.close(); assertTrue(worker(reader).awaitTermination(10, TimeUnit.SECONDS)); }
        assertEquals(baseline, requestCount());
        assertNoCaptureOrNetwork();
    }

    private static void assertReleased(ClipTranscriber reader, int baseline) throws Exception {
        assertSame("Public callbacks stay on main", Looper.getMainLooper(), Looper.myLooper());
        assertEquals("Callback must follow exact native request release", 0, field("activeRequest").getLong(reader));
        assertFalse("Public completion must make the same reader available", ((AtomicBoolean) field("busy").get(reader)).get());
        assertEquals("No native request should survive terminal delivery", baseline, requestCount());
    }
    private static void assertKnownBoundedWords(List<SubtitleCue> cues, long durationMs) {
        assertNotNull(cues); assertFalse(cues.isEmpty());
        StringBuilder text = new StringBuilder();
        for (SubtitleCue cue : cues) {
            assertTrue(cue.startMs >= 0 && cue.endMs > cue.startMs && cue.endMs <= durationMs);
            text.append(cue.text).append(' ');
        }
        String words = text.toString().toLowerCase(Locale.ROOT);
        assertTrue(words.contains("jacket")); assertTrue(words.contains("green")); assertTrue(words.contains("outfit"));
    }
    private static void failCallback(AtomicReference<Throwable> failure, CountDownLatch done, String message) {
        failure.set(new AssertionError(message)); done.countDown();
    }
    private static Field field(String name) throws Exception { Field field = ClipTranscriber.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private static ExecutorService worker(ClipTranscriber reader) throws Exception { return (ExecutorService) field("worker").get(reader); }
    private static int requestCount() throws Exception {
        Method method = ClipTranscriber.class.getDeclaredMethod("nativeRequestCountForDiagnostics"); method.setAccessible(true);
        return (Integer) method.invoke(null);
    }
    private static final class Source {
        final File file;
        final Uri uri;
        final long bytes, modified, durationMs;
        final byte[] hash;
        Source(String name) throws Exception {
            file = new File(context().getFilesDir(), "fixtures/" + name);
            assertTrue("Existing labelled synthetic fixture is required", file.isFile());
            uri = Uri.fromFile(file); bytes = file.length(); modified = file.lastModified(); hash = sha(file);
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            try {
                metadata.setDataSource(context(), uri);
                durationMs = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } finally { metadata.release(); }
            assertTrue("Known synthetic speech duration", durationMs >= 5000 && durationMs <= 9000);
        }
        void assertUnchanged() throws Exception {
            assertEquals(bytes, file.length()); assertEquals(modified, file.lastModified()); assertArrayEquals(hash, sha(file));
        }
    }
    private static byte[] sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) if (count > 0) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }
    private static void assertNoCaptureOrNetwork() throws Exception {
        Context context = context();
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals(Manifest.permission.INTERNET, permission);
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void ui(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
}
