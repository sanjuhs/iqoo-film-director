package dev.minifilm.director;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Fake recorder only: these bytes are not AAC and do not prove microphone/container support. */
@RunWith(AndroidJUnit4.class)
public final class LocalBriefRecorderTest {
    private static final byte[] BYTES = "Synthetic owned voice-brief bytes; no microphone".getBytes(StandardCharsets.UTF_8);

    @Test public void explicitFinishStopsAndReleasesBeforeTransferAndClosePreservesOpenReturnedFile() throws Exception {
        Harness h = new Harness(45_000, true);
        try {
            main(() -> { assertTrue(h.recorder.start()); assertTrue(h.recorder.isRecording()); assertFalse(h.recorder.start()); h.recorder.finish(); });
            assertEquals(List.of("started", "ready"), h.events);
            assertEquals(1, h.factory.backends.get(0).stops); assertEquals(1, h.factory.backends.get(0).releases);
            File result = h.ready.get(); assertNotNull(result); assertTrue(result.getName().endsWith(".m4a"));
            try (FileInputStream consuming = new FileInputStream(result)) {
                main(() -> { h.recorder.cancel(); h.recorder.close(); });
                assertTrue("Transferred file must outlive helper close while ASR reads it", result.isFile());
                assertArrayEquals(BYTES, consuming.readAllBytes());
            }
            assertEquals(List.of("started", "ready"), h.events);
        } finally { h.close(); }
    }

    @Test public void cancelDeletesOnlyItsOwnedRecordingAndNeverDeliversReady() throws Exception {
        Harness h = new Harness(45_000, true);
        File unrelated = File.createTempFile("brief-unrelated-original-", ".bin", context().getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(unrelated)) { output.write(BYTES); }
            main(() -> { assertTrue(h.recorder.start()); h.recorder.cancel(); h.recorder.cancel(); assertFalse(h.recorder.isRecording()); });
            assertEquals(List.of("started", "canceled"), h.events); assertNull(h.ready.get());
            assertFalse(h.factory.outputs.get(0).exists()); assertEquals(1, h.factory.backends.get(0).releases);
            assertArrayEquals(BYTES, java.nio.file.Files.readAllBytes(unrelated.toPath()));
        } finally { h.close(); unrelated.delete(); }
    }

    @Test public void refusedGuardCannotCreateBackendOrTemporaryRecording() {
        Harness h = new Harness(45_000, false);
        try { main(() -> { assertFalse(h.recorder.start()); assertFalse(h.recorder.isRecording()); });
            assertEquals(List.of("error"), h.events); assertTrue(h.factory.outputs.isEmpty());
        } finally { h.close(); }
    }

    @Test public void creationStartStopAndEmptyOutputFailuresDeleteOwnedFiles() {
        for (String failure : new String[] {"create", "start", "stop", "empty"}) {
            Harness h = new Harness(45_000, true); h.factory.failure = failure;
            try {
                main(() -> {
                    boolean started = h.recorder.start();
                    if (failure.equals("create") || failure.equals("start")) assertFalse(started);
                    else { assertTrue(started); h.recorder.finish(); }
                    assertFalse(h.recorder.isRecording());
                });
                assertNull(h.ready.get()); assertEquals("error", h.events.get(h.events.size() - 1));
                assertEquals(1, h.factory.outputs.size()); assertFalse(h.factory.outputs.get(0).exists());
                if (!failure.equals("create")) assertEquals("Release is attempted even after start/stop failure", 1, h.factory.backends.get(0).releases);
            } finally { h.close(); }
        }
    }

    @Test public void hardTimerReleasesAndDiscardsTheMatchingRecordingWithoutPassingPartialAudio() throws Exception {
        Harness h = new Harness(40, true);
        try {
            main(() -> assertTrue(h.recorder.start()));
            assertTrue("Bounded test timer must finish", h.terminal.await(3, TimeUnit.SECONDS));
            main(() -> assertFalse(h.recorder.isRecording()));
            assertEquals(List.of("started", "error"), h.events); assertNull(h.ready.get());
            assertFalse(h.factory.outputs.get(0).exists());
            assertEquals(1, h.factory.backends.get(0).stops); assertEquals(1, h.factory.backends.get(0).releases);
        } finally { h.close(); }
    }

    @Test public void releaseFailureProtectsOutputUntilLaterCloseSuccessfullyReleasesAndReclaimsIt() {
        Harness h = new Harness(45_000, true); h.factory.failure = "release";
        try {
            main(() -> { assertTrue(h.recorder.start()); h.recorder.finish(); });
            File protectedFile = h.factory.outputs.get(0); FakeBackend backend = h.factory.backends.get(0);
            assertEquals(List.of("started", "error"), h.events); assertNull(h.ready.get());
            assertTrue("A failed release must not unlink its potentially open output", protectedFile.isFile());
            main(() -> assertTrue(h.recorder.hasUnreleasedResources()));
            assertEquals("Protected output remains reserved against the quota", Boolean.FALSE, ownership(protectedFile));
            assertFalse(LocalBriefRecorder.discard(protectedFile)); assertEquals(1, backend.releases);
            main(h.recorder::close);
            assertTrue(protectedFile.isFile()); assertFalse(LocalBriefRecorder.discard(protectedFile));
            assertEquals("Close retries retained backend without pretending release succeeded", 2, backend.releases);
            backend.throwRelease = false;
            main(h.recorder::close); // A closed helper may retry unresolved native cleanup.
            main(() -> assertFalse(h.recorder.hasUnreleasedResources()));
            assertFalse(protectedFile.exists()); assertEquals(3, backend.releases);
            assertEquals("Stop is not blindly repeated after a previous attempt", 1, backend.stops);
            assertEquals(List.of("started", "error"), h.events);
            assertFalse("Successful cleanup removed the ownership reservation", LocalBriefRecorder.discard(protectedFile));
            assertNull(ownership(protectedFile));
        } finally { h.close(); }
    }

    @Test public void cancelReleaseFailureReportsErrorKeepsProtectionAndLaterCancelCanRecover() {
        Harness h = new Harness(45_000, true); h.factory.failure = "release";
        try {
            main(() -> { assertTrue(h.recorder.start()); h.recorder.cancel(); });
            File protectedFile = h.factory.outputs.get(0); FakeBackend backend = h.factory.backends.get(0);
            assertEquals(List.of("started", "error"), h.events); assertTrue(protectedFile.exists());
            main(() -> assertTrue(h.recorder.hasUnreleasedResources()));
            assertFalse(LocalBriefRecorder.discard(protectedFile));
            assertEquals(Boolean.FALSE, ownership(protectedFile));
            main(() -> assertFalse("An unresolved recorder must prevent another microphone session", h.recorder.start()));
            assertEquals(1, h.factory.outputs.size()); assertTrue(protectedFile.exists());
            backend.throwRelease = false; main(h.recorder::cancel);
            main(() -> assertFalse(h.recorder.hasUnreleasedResources()));
            assertFalse(protectedFile.exists()); assertEquals(1, backend.stops); assertEquals(3, backend.releases);
            assertNull(ownership(protectedFile));
            assertNull(h.ready.get());
        } finally { h.close(); }
    }

    @Test public void staleErrorAndOldTimerCannotStopReplacementAndCloseSuppressesPendingError() {
        Harness h = new Harness(45_000, true); AtomicReference<Runnable> oldTimer = new AtomicReference<>();
        try {
            main(() -> {
                assertTrue(h.recorder.start());
                Object run = field(h.recorder, "active"); oldTimer.set((Runnable) field(run, "timeout"));
                h.recorder.cancel(); assertTrue(h.recorder.start());
                oldTimer.get().run(); h.factory.backends.get(0).error.run(); h.factory.backends.get(0).limit.run();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            main(() -> {
                assertTrue(h.recorder.isRecording()); assertEquals(List.of("started", "canceled", "started"), h.events);
                h.factory.backends.get(1).error.run(); h.recorder.close();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(List.of("started", "canceled", "started"), h.events);
            for (File file : h.factory.outputs) assertFalse(file.exists());
            assertEquals(1, h.factory.backends.get(1).stops); assertEquals(1, h.factory.backends.get(1).releases);
        } finally { h.close(); }
    }

    @Test public void currentBackendErrorReleasesAndDeletesBeforeErrorCallback() {
        Harness h = new Harness(45_000, true);
        try {
            main(() -> { assertTrue(h.recorder.start()); h.factory.backends.get(0).error.run(); });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(List.of("started", "error"), h.events); assertNull(h.ready.get());
            assertFalse(h.factory.outputs.get(0).exists()); assertEquals(1, h.factory.backends.get(0).releases);
        } finally { h.close(); }
    }

    @Test public void encoderLimitDiscardsReleasedFileAndDoesNotDeliverItForTranscription() {
        Harness h = new Harness(45_000, true);
        try {
            main(() -> { assertTrue(h.recorder.start()); h.factory.backends.get(0).limit.run(); });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(List.of("started", "error"), h.events); assertNull(h.ready.get());
            assertFalse(h.factory.outputs.get(0).exists()); assertEquals(1, h.factory.backends.get(0).releases);
        } finally { h.close(); }
    }

    @Test public void fourTransferredFilesStayCountedAcrossHelpersUntilSafeDiscardAndFailedDeleteStaysCounted() throws Exception {
        List<Harness> held = new ArrayList<>(); Harness retry = null; File child = null;
        try {
            for (int i = 0; i < 4; i++) {
                Harness h = new Harness(45_000, true); held.add(h);
                main(() -> { assertTrue(h.recorder.start()); h.recorder.finish(); h.recorder.close(); });
                assertTrue(h.ready.get().isFile());
            }
            retry = new Harness(45_000, true); Harness fifth = retry;
            main(() -> assertFalse("Activity/helper replacement must not bypass retained-file quota", fifth.recorder.start()));
            assertTrue(fifth.factory.outputs.isEmpty());
            File first = held.get(0).ready.get();
            // Synthetic filesystem failure at an owned path, not an unrelated creator source.
            assertTrue(first.delete()); assertTrue(first.mkdir()); child = new File(first, "synthetic-blocker.txt");
            try (FileOutputStream output = new FileOutputStream(child)) { output.write(BYTES); }
            assertFalse("Failed deletion keeps its reservation", LocalBriefRecorder.discard(first));
            main(() -> assertFalse(fifth.recorder.start()));
            assertTrue(child.delete()); assertTrue(LocalBriefRecorder.discard(first));
            main(() -> { assertTrue(fifth.recorder.start()); fifth.recorder.finish(); });
            assertTrue(fifth.ready.get().isFile());
        } finally {
            if (child != null) child.delete();
            for (Harness h : held) h.close();
            if (retry != null) retry.close();
        }
    }

    @Test public void discardRejectsUnrelatedAndStillRecordingFiles() throws Exception {
        Harness h = new Harness(45_000, true);
        File original = File.createTempFile("brief-original-preserved-", ".bin", context().getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(original)) { output.write(BYTES); }
            assertFalse(LocalBriefRecorder.discard(original));
            main(() -> {
                assertTrue(h.recorder.start());
                assertFalse(LocalBriefRecorder.discard(h.factory.outputs.get(0)));
                assertTrue(h.factory.outputs.get(0).exists()); h.recorder.cancel();
            });
            assertArrayEquals(BYTES, java.nio.file.Files.readAllBytes(original.toPath()));
        } finally { h.close(); original.delete(); }
    }

    @Test public void firstProcessReserveReconcilesOnlyExactOrphansAndRecreationNeverRescansLiveReaders() throws Exception {
        main(LocalBriefRecorder::resetReconciliationForTest);
        File cache = context().getCacheDir();
        String number = Long.toString(Math.floorMod(System.nanoTime(), 1_000_000_000_000_000L));
        File orphan = new File(cache, "minifilm-brief-" + number + ".m4a");
        File later = new File(cache, "minifilm-brief-" + number + "1.m4a");
        File foreign = new File(cache, "minifilm-brief-not-owned-" + number + ".m4a");
        File directory = new File(cache, "minifilm-brief-" + number + "2.m4a");
        Harness first = null, replacement = null;
        try {
            for (File file : new File[] {orphan, later, foreign, directory}) assertFalse("Synthetic test path must be fresh", file.exists());
            for (File file : new File[] {orphan, foreign}) try (FileOutputStream out = new FileOutputStream(file)) { out.write(BYTES); }
            assertTrue(directory.mkdir());
            first = new Harness(45_000, true); Harness initial = first;
            main(() -> { assertTrue(initial.recorder.start()); initial.recorder.finish(); initial.recorder.close(); });
            assertFalse("A crashed prior process has no remaining ASR reader", orphan.exists());
            assertTrue("Unrelated cache naming must be preserved", foreign.isFile());
            assertTrue("Matching directories must not be deleted", directory.isDirectory());
            File reading = initial.ready.get();
            try (FileInputStream reader = new FileInputStream(reading)) {
                try (FileOutputStream out = new FileOutputStream(later)) { out.write(BYTES); }
                replacement = new Harness(45_000, true); Harness next = replacement;
                main(() -> { assertTrue(next.recorder.start()); next.recorder.cancel(); });
                assertTrue("Another Activity/helper must not rerun orphan scanning", later.isFile());
                assertTrue("Current-process returned reader stays protected from scanning", reading.isFile());
                assertArrayEquals(BYTES, reader.readAllBytes());
                assertEquals(Boolean.TRUE, ownership(reading));
            }
        } finally {
            if (first != null) first.close(); if (replacement != null) replacement.close();
            orphan.delete(); later.delete(); foreign.delete(); directory.delete();
        }
    }

    @Test public void publicOperationsRejectWorkerThreadBeforeBackendAccess() {
        Harness h = new Harness(45_000, true);
        try {
            assertNotEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper());
            try { h.recorder.start(); fail("Worker start must be rejected"); } catch (IllegalStateException expected) { }
            assertTrue(h.factory.outputs.isEmpty()); assertTrue(h.events.isEmpty());
        } finally { h.close(); }
    }

    private static final class Harness {
        final FakeFactory factory = new FakeFactory(); final List<String> events = new ArrayList<>();
        final AtomicReference<File> ready = new AtomicReference<>(); final CountDownLatch terminal = new CountDownLatch(1);
        final LocalBriefRecorder recorder;
        Harness(long duration, boolean allowed) {
            AtomicReference<LocalBriefRecorder> created = new AtomicReference<>();
            main(() -> created.set(new LocalBriefRecorder(context(), new LocalBriefRecorder.Listener() {
                public void onStarted() { assertMain(); events.add("started"); }
                public void onReady(File file) {
                    assertMain(); FakeBackend backend = factory.backends.get(factory.backends.size() - 1);
                    assertEquals(1, backend.stops); assertEquals(1, backend.releases);
                    assertTrue(file.isFile()); ready.set(file); events.add("ready"); terminal.countDown();
                }
                public void onError(String message) { assertMain(); events.add("error"); terminal.countDown(); }
                public void onCanceled() { assertMain(); events.add("canceled"); terminal.countDown(); }
            }, factory, () -> allowed, duration)));
            recorder = created.get();
        }
        void close() { for (FakeBackend backend : factory.backends) backend.throwRelease = false; main(recorder::close); for (File file : factory.outputs) LocalBriefRecorder.discard(file); }
    }
    private static final class FakeFactory implements LocalBriefRecorder.Factory {
        final List<File> outputs = new ArrayList<>(); final List<FakeBackend> backends = new ArrayList<>(); String failure = "";
        public LocalBriefRecorder.RecorderBackend create(File output, Runnable error, Runnable limit) throws Exception {
            assertMain(); outputs.add(output); if (failure.equals("create")) throw new Exception("Synthetic creation failure");
            FakeBackend backend = new FakeBackend(output, error, limit, failure); backends.add(backend); return backend;
        }
    }
    private static final class FakeBackend implements LocalBriefRecorder.RecorderBackend {
        final File file; final Runnable error, limit; final String failure; int starts, stops, releases; boolean throwRelease;
        FakeBackend(File file, Runnable error, Runnable limit, String failure) { this.file = file; this.error = error; this.limit = limit; this.failure = failure; this.throwRelease = failure.equals("release"); }
        public void start() throws Exception { assertMain(); starts++; if (failure.equals("start")) throw new Exception("Synthetic start failure"); }
        public void stop() throws Exception {
            assertMain(); stops++; if (failure.equals("stop")) throw new Exception("Synthetic stop failure");
            if (!failure.equals("empty")) try (FileOutputStream output = new FileOutputStream(file)) { output.write(BYTES); }
        }
        public void release() { assertMain(); releases++; if (throwRelease) throw new IllegalStateException("Synthetic release failure"); }
    }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object ownership(File file) {
        try { Field field = LocalBriefRecorder.class.getDeclaredField("OWNED"); field.setAccessible(true);
            @SuppressWarnings("unchecked") java.util.Map<File, Boolean> owned = (java.util.Map<File, Boolean>) field.get(null);
            synchronized (owned) { return owned.get(file); }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void assertMain() { assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper()); }
    private static void main(Runnable work) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try { work.run(); } catch (Throwable caught) { error.set(caught); } });
        if (error.get() != null) throw new AssertionError("Main-thread operation failed", error.get());
    }
}
