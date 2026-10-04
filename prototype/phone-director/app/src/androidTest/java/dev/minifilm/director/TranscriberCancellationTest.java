package dev.minifilm.director;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Synthetic PCM/native lifetime and existing synthetic clip only. No microphone or playback. */
@RunWith(AndroidJUnit4.class)
public final class TranscriberCancellationTest {
    private static final String TAG = "MiniFilmASRCancelTest";
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private String missingModel() {
        File missing = new File(context().getCacheDir(), "test-missing-tiny-en-model-never-created.bin");
        assertFalse(missing.exists());
        return missing.getAbsolutePath();
    }
    private float[] voicedPcm() {
        float[] pcm = new float[1600];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (i & 1) == 0 ? 0.1f : -0.1f;
        return pcm;
    }

    @Test(timeout = 15_000) public void precancelledRequestStaysCancelledAfterAnotherNativeEntry() throws Exception {
        ClipTranscriber loader = new ClipTranscriber(context());
        int baseline = requestCount(); long cancelled = createRequest(), other = createRequest();
        try {
            cancelRequest(cancelled);
            long started = SystemClock.elapsedRealtime();
            assertEquals("Transcription cancelled.", transcribe(cancelled, voicedPcm()).getString("error"));
            assertEquals(0, transcribe(other, new float[1600]).getJSONArray("segments").length());
            // The old shared flag was reset at every native entry. Another request must never
            // turn this cancelled request into a missing-model load attempt or a successful result.
            assertEquals("Transcription cancelled.", transcribe(cancelled, voicedPcm()).getString("error"));
            assertTrue(SystemClock.elapsedRealtime() - started < 5000);
            Log.i(TAG, "NATIVE_PRE_CANCEL_OK independentEntry=true missingModelNeverOpenedForCancelled=true syntheticPcm=true");
        } finally { freeRequest(cancelled); freeRequest(other); loader.close(); }
        assertEquals(baseline, requestCount());
    }

    @Test(timeout = 20_000) public void concurrentCancelAndFreeCannotAffectIndependentRequestOrReuseId() throws Exception {
        ClipTranscriber loader = new ClipTranscriber(context()); int baseline = requestCount();
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 24; iteration++) {
                long cancelled = createRequest(), other = createRequest();
                assertNotEquals(cancelled, other);
                cancelRequest(cancelled);
                CountDownLatch go = new CountDownLatch(1);
                Future<String> cancelledResult = threads.submit(() -> {
                    go.await(); return transcribe(cancelled, voicedPcm()).getString("error");
                });
                Future<Integer> otherResult = threads.submit(() -> {
                    go.await(); return transcribe(other, new float[1600]).getJSONArray("segments").length();
                });
                try {
                    go.countDown(); freeRequest(cancelled); freeRequest(cancelled); cancelRequest(cancelled);
                    String error = cancelledResult.get(5, TimeUnit.SECONDS);
                    assertTrue(error, error.equals("Transcription cancelled.") || error.equals("Transcription request is unavailable."));
                    assertEquals(Integer.valueOf(0), otherResult.get(5, TimeUnit.SECONDS));
                    assertEquals("Transcription request is unavailable.", transcribe(cancelled, voicedPcm()).getString("error"));
                } finally { go.countDown(); freeRequest(cancelled); freeRequest(other); }
            }
            Log.i(TAG, "NATIVE_REQUEST_ISOLATION_OK concurrentCancelFreeCases=24 staleIdUnavailable=true syntheticPcm=true");
        } finally { threads.shutdownNow(); threads.awaitTermination(5, TimeUnit.SECONDS); loader.close(); }
        assertEquals(baseline, requestCount());
    }

    @Test(timeout = 20_000) public void closeCancelsOnlyItsQueuedRequestAndOrderlyWorkerCleanupFreesBoth() throws Exception {
        ClipTranscriber first = new ClipTranscriber(context()), second = new ClipTranscriber(context());
        assertTrue(first.isModelAvailable()); assertTrue(second.isModelAvailable()); int baseline = requestCount();
        ExecutorService firstWorker = worker(first), secondWorker = worker(second);
        CountDownLatch held = new CountDownLatch(2), release = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        firstWorker.execute(() -> holdWorker(held, release)); secondWorker.execute(() -> holdWorker(held, release));
        long firstId = 0, secondId = 0;
        try {
            assertTrue(held.await(3, TimeUnit.SECONDS));
            onMain(() -> {
                first.transcribe(Uri.parse("file:///synthetic/not-opened-first.mp4"), countingListener(callbacks));
                second.transcribe(Uri.parse("file:///synthetic/not-opened-second.mp4"), countingListener(callbacks));
            });
            firstId = activeRequest(first); secondId = activeRequest(second);
            assertNotEquals(0, firstId); assertNotEquals(0, secondId); assertEquals(baseline + 2, requestCount());
            first.close();
            assertEquals("Transcription cancelled.", transcribe(firstId, voicedPcm()).getString("error"));
            assertEquals("Closing another Java instance must not cancel this native request", 0,
                    transcribe(secondId, new float[1600]).getJSONArray("segments").length());
            assertEquals("Transcription cancelled.", transcribe(firstId, voicedPcm()).getString("error"));
            second.close(); release.countDown();
            assertTrue(firstWorker.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(secondWorker.awaitTermination(5, TimeUnit.SECONDS)); drainMain();
            assertEquals(0, callbacks.get()); assertEquals(0, activeRequest(first)); assertEquals(0, activeRequest(second));
            assertEquals(baseline, requestCount());
            assertEquals("Transcription request is unavailable.", transcribe(firstId, voicedPcm()).getString("error"));
            assertEquals("Transcription request is unavailable.", transcribe(secondId, voicedPcm()).getString("error"));
            Log.i(TAG, "JAVA_QUEUED_CLOSE_OK instances=2 crossCancellation=false queuedDecodeSkipped=true registryReleased=true");
        } finally {
            first.close(); second.close(); release.countDown();
            firstWorker.awaitTermination(5, TimeUnit.SECONDS); secondWorker.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test(timeout = 15_000) public void rejectedWorkerSubmissionReleasesRequestAndReturnsSafeMainError() throws Exception {
        ClipTranscriber transcriber = new ClipTranscriber(context()); assertTrue(transcriber.isModelAvailable());
        int baseline = requestCount(); worker(transcriber).shutdown();
        CountDownLatch done = new CountDownLatch(1); AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<String> error = new AtomicReference<>(); AtomicBoolean mainThread = new AtomicBoolean();
        try {
            onMain(() -> transcriber.transcribe(Uri.parse("file:///synthetic/private-source-do-not-disclose.mp4"),
                    new ClipTranscriber.Listener() {
                        @Override public void onComplete(List<SubtitleCue> cues, long elapsed) { callbacks.incrementAndGet(); done.countDown(); }
                        @Override public void onError(String message) {
                            callbacks.incrementAndGet(); error.set(message); mainThread.set(Looper.myLooper() == Looper.getMainLooper()); done.countDown();
                        }
                    }));
            assertTrue(done.await(5, TimeUnit.SECONDS)); assertEquals(1, callbacks.get()); assertTrue(mainThread.get());
            assertEquals("The local speech worker could not start. Use manual text.", error.get());
            assertFalse(error.get().contains("private-source")); assertEquals(0, activeRequest(transcriber));
            assertFalse(busy(transcriber)); assertEquals(baseline, requestCount());
            Log.i(TAG, "JAVA_REJECTED_SUBMISSION_OK registryReleased=true busyCleared=true safeMainError=true");
        } finally { transcriber.close(); }
    }

    @Test(timeout = 10_000) public void closeSuppressesPriorQueuedErrorsButExplicitClosedCallKeepsFixedApiError() throws Exception {
        ClipTranscriber transcriber = new ClipTranscriber(context()); int baseline = requestCount();
        AtomicInteger prior = new AtomicInteger(), closedCalls = new AtomicInteger();
        AtomicReference<String> error = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
        onMain(() -> {
            transcriber.analyzeForTrim(Uri.parse("file:///synthetic/not-read.mp4"), -1, 1000,
                    new ClipTranscriber.TrimListener() {
                        @Override public void onComplete(SpeechTrim trim, long elapsed) { prior.incrementAndGet(); }
                        @Override public void onError(String message) { prior.incrementAndGet(); }
                    });
            transcriber.close();
            transcriber.transcribe(Uri.parse("file:///synthetic/not-read.mp4"), new ClipTranscriber.Listener() {
                @Override public void onComplete(List<SubtitleCue> cues, long elapsed) { closedCalls.incrementAndGet(); }
                @Override public void onError(String message) { closedCalls.incrementAndGet(); error.set(message); }
            });
            new Handler(Looper.getMainLooper()).post(done::countDown);
        });
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertEquals(0, prior.get()); assertEquals(1, closedCalls.get());
        assertEquals("Transcriber is closed.", error.get()); assertEquals(baseline, requestCount());
    }

    @Test(timeout = 100_000) public void actualExistingPaddedSyntheticSpeechStillTranscribesWithoutChangingSource() throws Exception {
        File source = new File(context().getFilesDir(), "fixtures/jacket-speech-padded.mp4");
        assertTrue("Existing labelled synthetic English fixture is required", source.isFile());
        String hash = sha(source); int baseline = requestCount(); ClipTranscriber transcriber = new ClipTranscriber(context());
        assertTrue(transcriber.isModelAvailable()); CountDownLatch done = new CountDownLatch(1);
        AtomicReference<List<SubtitleCue>> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger(); AtomicBoolean mainThread = new AtomicBoolean();
        try {
            onMain(() -> transcriber.transcribe(Uri.fromFile(source), new ClipTranscriber.Listener() {
                @Override public void onComplete(List<SubtitleCue> cues, long elapsed) {
                    result.set(cues); callbacks.incrementAndGet(); mainThread.set(Looper.myLooper() == Looper.getMainLooper());
                    Log.i(TAG, "ACTUAL_SYNTHETIC_ASR_RESULT backend=CPU model=tiny.en segments=" + cues.size() + " elapsedMs=" + elapsed);
                    done.countDown();
                }
                @Override public void onError(String message) { error.set(message); callbacks.incrementAndGet(); done.countDown(); }
            }));
            assertTrue(done.await(80, TimeUnit.SECONDS)); assertNull(error.get()); assertNotNull(result.get());
            assertEquals(1, callbacks.get()); assertTrue(mainThread.get()); assertFalse(result.get().isEmpty());
            StringBuilder text = new StringBuilder();
            for (SubtitleCue cue : result.get()) {
                assertTrue(cue.startMs >= 0 && cue.endMs > cue.startMs && cue.endMs <= 9000);
                text.append(cue.text).append(' ');
            }
            String words = text.toString().toLowerCase(Locale.ROOT);
            assertTrue(words.contains("jacket")); assertTrue(words.contains("green")); assertTrue(words.contains("outfit"));
            assertEquals(hash, sha(source));
        } finally { transcriber.close(); assertTrue(worker(transcriber).awaitTermination(10, TimeUnit.SECONDS)); }
        assertEquals(baseline, requestCount());
        Log.i(TAG, "ACTUAL_SYNTHETIC_ASR_OK knownWords=jacket,green,outfit sourceUnchanged=true registryReleased=true microphone=false playback=false");
    }

    private ClipTranscriber.Listener countingListener(AtomicInteger count) {
        return new ClipTranscriber.Listener() {
            @Override public void onComplete(List<SubtitleCue> cues, long elapsed) { count.incrementAndGet(); }
            @Override public void onError(String message) { count.incrementAndGet(); }
        };
    }
    private static void holdWorker(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test barrier not released"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    private void onMain(Runnable runnable) { InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable); }
    private void drainMain() throws Exception {
        CountDownLatch drained = new CountDownLatch(1); new Handler(Looper.getMainLooper()).post(drained::countDown);
        assertTrue(drained.await(5, TimeUnit.SECONDS));
    }
    private static ExecutorService worker(ClipTranscriber instance) throws Exception { return (ExecutorService) field("worker").get(instance); }
    private static long activeRequest(ClipTranscriber instance) throws Exception { return field("activeRequest").getLong(instance); }
    private static boolean busy(ClipTranscriber instance) throws Exception {
        return ((java.util.concurrent.atomic.AtomicBoolean) field("busy").get(instance)).get();
    }
    private static Field field(String name) throws Exception { Field field = ClipTranscriber.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Object nativeCall(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = ClipTranscriber.class.getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(null, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception) throw (Exception) error.getCause();
            throw error;
        }
    }
    private static long createRequest() throws Exception { return (Long) nativeCall("nativeCreateRequest", new Class<?>[0]); }
    private static void cancelRequest(long id) throws Exception { nativeCall("nativeCancelRequest", new Class<?>[]{long.class}, id); }
    private static void freeRequest(long id) throws Exception { nativeCall("nativeFreeRequest", new Class<?>[]{long.class}, id); }
    private static int requestCount() throws Exception { return (Integer) nativeCall("nativeRequestCountForDiagnostics", new Class<?>[0]); }
    private JSONObject transcribe(long id, float[] pcm) throws Exception {
        return new JSONObject((String) nativeCall("nativeTranscribe", new Class<?>[]{long.class, String.class, float[].class}, id, missingModel(), pcm));
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) { int count; while ((count = input.read(buffer)) >= 0) if (count > 0) digest.update(buffer, 0, count); }
        StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255)); return result.toString();
    }
}
