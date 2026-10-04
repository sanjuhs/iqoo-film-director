package dev.minifilm.director;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Model-free lifecycle tests of the real batch coordinator. No Activity, media I/O or playback. */
@RunWith(AndroidJUnit4.class)
public final class SubtitleBatchTest {
    @Test public void duplicateSourceReadsOnceAndIndependentDraftsWaitForIdleWithErrorContinuation() {
        Factory factory = new Factory();
        SubtitleBatch batch = new SubtitleBatch(context(), factory);
        Take first = take("same"), duplicate = take("same"), failed = take("failed"), existing = take("existing"), unselected = take("unselected");
        SubtitleCue originalWords = new SubtitleCue(0, 700, "Creator words");
        existing.subtitles.add(originalWords);
        unselected.selected = false;
        Events events = new Events();
        try {
            ui(() -> batch.start(Arrays.asList(first, duplicate, failed, existing, unselected), events));
            assertEquals(1, factory.readers.size());
            Reader reader = factory.readers.get(0);
            SubtitleCue supplied = new SubtitleCue(0, 600, "Synthetic words");
            ui(() -> { reader.complete(Arrays.asList(supplied)); supplied.text = "Changed after callback"; reader.error(); });
            drain();
            assertEquals(1, reader.closes);
            assertEquals(0, events.drafts.size());
            assertEquals("No next reader before actual idle", 1, factory.readers.size());
            ui(reader::release);
            drain();
            assertEquals(2, factory.readers.size());
            assertEquals(Arrays.asList(first, duplicate), events.targets);
            assertEquals("Synthetic words", events.drafts.get(0).get(0).text);
            assertNotSame(events.drafts.get(0), events.drafts.get(1));
            assertNotSame(events.drafts.get(0).get(0), events.drafts.get(1).get(0));
            events.drafts.get(0).get(0).text = "Only first copy";
            assertEquals("Synthetic words", events.drafts.get(1).get(0).text);
            assertTrue("Coordinator itself must not apply or overwrite words", first.subtitles.isEmpty() && duplicate.subtitles.isEmpty());
            Reader second = factory.readers.get(1);
            ui(second::error);
            drain();
            assertEquals(0, events.completed);
            ui(second::release);
            drain();
            assertEquals(1, events.completed);
            assertArrayEquals(new int[]{2, 1, 2}, events.counts);
            assertEquals(Arrays.asList("0/2", "1/2", "2/2"), events.progress);
            assertSame(originalWords, existing.subtitles.get(0));
            assertEquals("Creator words", originalWords.text);
            ui(() -> assertFalse(batch.isRunning()));
        } finally { ui(batch::close); }
    }

    @Test public void cancelKeepsDeliveredDraftAndAcknowledgesOnlyIdleThenAllowsFreshRun() {
        Factory factory = new Factory();
        SubtitleBatch batch = new SubtitleBatch(context(), factory);
        Events old = new Events(), retry = new Events(), refused = new Events();
        try {
            ui(() -> batch.start(Arrays.asList(take("first"), take("second")), old));
            Reader first = factory.readers.get(0);
            ui(() -> first.complete(words())); drain(); ui(first::release); drain();
            assertEquals(1, old.drafts.size());
            Reader pending = factory.readers.get(1);
            ui(() -> { batch.cancel(); assertTrue(batch.isRunning()); batch.start(Arrays.asList(take("refused")), refused); });
            drain();
            assertEquals(1, refused.errors.size());
            assertEquals(0, old.cancelled);
            ui(() -> { pending.complete(words()); pending.error(); }); drain();
            assertEquals(1, old.drafts.size());
            ui(pending::release); drain();
            assertEquals(1, old.cancelled);
            assertEquals(1, old.cancelledDrafted);
            assertEquals(0, old.completed);
            ui(() -> { assertFalse(batch.isRunning()); batch.start(Arrays.asList(take("retry")), retry); });
            Reader fresh = factory.readers.get(2);
            ui(() -> { pending.complete(words()); pending.release(); fresh.complete(words()); }); drain();
            assertEquals(0, retry.completed);
            ui(fresh::release); drain();
            assertEquals(1, retry.completed);
            assertArrayEquals(new int[]{1, 0, 0}, retry.counts);
            assertEquals(1, old.drafts.size());
        } finally { ui(batch::close); }
    }

    @Test public void changedSourceDurationSelectionOrMissingListCannotOverwriteCreatorState() {
        Factory factory = new Factory();
        SubtitleBatch batch = new SubtitleBatch(context(), factory);
        Take uri = take("shared"), duration = take("shared"), selection = take("shared"), list = take("shared"), edited = take("shared");
        Events events = new Events();
        try {
            ui(() -> batch.start(Arrays.asList(uri, duration, selection, list, edited), events));
            Reader reader = factory.readers.get(0);
            ui(() -> reader.complete(words())); drain();
            ui(() -> {
                uri.uri = Uri.parse("file:///synthetic/changed.mp4");
                duration.durationMs++;
                selection.selected = false;
                list.subtitles = new ArrayList<>();
                edited.subtitles.add(new SubtitleCue(0, 300, "My edit"));
                reader.release();
            });
            drain();
            assertEquals(0, events.drafts.size());
            assertArrayEquals(new int[]{0, 0, 5}, events.counts);
            assertEquals("My edit", edited.subtitles.get(0).text);
            assertEquals(1, factory.readers.size());
        } finally { ui(batch::close); }
    }

    @Test public void malformedCuesFailOnlyTheirSourcesAndSilencePreservesMissingWords() {
        Factory factory = new Factory();
        SubtitleBatch batch = new SubtitleBatch(context(), factory);
        List<Take> takes = new ArrayList<>();
        for (int i = 0; i < 6; i++) takes.add(take("invalid" + i));
        List<List<SubtitleCue>> outputs = new ArrayList<>();
        outputs.add(null);
        outputs.add(Arrays.asList(new SubtitleCue(0, 600, "First"), new SubtitleCue(500, 800, "Overlap")));
        outputs.add(Arrays.asList(new SubtitleCue(0, 1001, "Outside source")));
        List<SubtitleCue> tooMany = new ArrayList<>();
        for (int i = 0; i < 501; i++) tooMany.add(new SubtitleCue(i, i + 1, "Word"));
        outputs.add(tooMany);
        outputs.add(Arrays.asList(new SubtitleCue(0, 600, " ")));
        outputs.add(Collections.emptyList());
        Events events = new Events();
        try {
            ui(() -> batch.start(takes, events));
            for (int i = 0; i < outputs.size(); i++) {
                Reader reader = factory.readers.get(i);
                List<SubtitleCue> output = outputs.get(i);
                ui(() -> reader.complete(output)); drain();
                assertEquals(i + 1, factory.readers.size());
                ui(reader::release); drain();
            }
            assertEquals(1, events.completed);
            assertArrayEquals(new int[]{0, 5, 1}, events.counts);
            assertTrue(events.drafts.isEmpty());
            for (Take take : takes) assertTrue(take.subtitles.isEmpty());
        } finally { ui(batch::close); }
    }

    @Test public void preflightRejectsWholeInvalidBatchAndCancelOrCloseSuppressQueuedErrors() {
        Factory factory = new Factory();
        SubtitleBatch batch = new SubtitleBatch(context(), factory);
        Events events = new Events();
        try {
            Take remote = take("remote"); remote.uri = Uri.parse("https://example.invalid/video.mp4");
            Take longClip = take("long"); longClip.durationMs = 180_001;
            Take zero = take("zero"); zero.durationMs = 0;
            List<Take> excessive = new ArrayList<>(); for (int i = 0; i < 13; i++) excessive.add(take("many" + i));
            for (List<Take> invalid : Arrays.asList(Arrays.asList(take("valid"), remote), Arrays.asList(longClip), Arrays.asList(zero), excessive)) {
                int before = events.errors.size();
                ui(() -> batch.start(invalid, events)); drain();
                assertEquals(before + 1, events.errors.size());
                assertEquals("Preflight must not partially start a reader", 0, factory.readers.size());
            }
            int errors = events.errors.size();
            ui(() -> { batch.start(Arrays.asList(remote), events); batch.cancel(); assertFalse(batch.isRunning()); }); drain();
            assertEquals(errors, events.errors.size());
            assertEquals(0, events.cancelled);
            factory.available = false;
            ui(() -> { batch.start(Arrays.asList(take("model")), events); batch.close(); batch.start(Arrays.asList(take("closed")), events); }); drain();
            assertEquals(errors, events.errors.size());
            assertEquals(0, factory.readers.size());
        } finally { ui(batch::close); }
    }

    @Test public void closeSuppressesQueuedResultsAndReentrantCancellationStartsNoExtraReader() {
        Factory factory = new Factory();
        SubtitleBatch batch = new SubtitleBatch(context(), factory);
        Events events = new Events();
        ui(() -> batch.start(Arrays.asList(take("closed")), events));
        Reader reader = factory.readers.get(0);
        ui(() -> { reader.complete(words()); batch.close(); }); drain();
        assertEquals(1, reader.closes);
        ui(() -> { reader.release(); reader.complete(words()); }); drain();
        assertTrue(events.drafts.isEmpty());
        assertEquals(0, events.completed + events.cancelled + events.errors.size());
        ui(() -> assertFalse(batch.isRunning()));

        Factory secondFactory = new Factory();
        SubtitleBatch reentrant = new SubtitleBatch(context(), secondFactory);
        Events cancelled = new Events() {
            @Override public void onProgress(int completed, int total) { super.onProgress(completed, total); if (completed == 0) reentrant.cancel(); }
        };
        try {
            ui(() -> reentrant.start(Arrays.asList(take("never-start")), cancelled)); drain();
            assertEquals(0, secondFactory.readers.size());
            assertEquals(1, cancelled.cancelled);
            assertEquals(0, cancelled.cancelledDrafted);
        } finally { ui(reentrant::close); }
    }

    private static class Events implements SubtitleBatch.Listener {
        final List<Take> targets = new ArrayList<>();
        final List<List<SubtitleCue>> drafts = new ArrayList<>();
        final List<String> progress = new ArrayList<>(), errors = new ArrayList<>();
        int completed, cancelled, cancelledDrafted;
        int[] counts;
        private void mainThread() { assertSame(Looper.getMainLooper(), Looper.myLooper()); }
        @Override public void onProgress(int completed, int total) { mainThread(); progress.add(completed + "/" + total); }
        @Override public void onDraft(Take target, List<SubtitleCue> cues, long elapsedMs) {
            mainThread(); assertEquals(7, elapsedMs); targets.add(target); drafts.add(cues);
        }
        @Override public void onComplete(int drafted, int failed, int skipped) { mainThread(); completed++; counts = new int[]{drafted, failed, skipped}; }
        @Override public void onCancelled(int drafted) { mainThread(); cancelled++; cancelledDrafted = drafted; }
        @Override public void onError(String message) { mainThread(); assertNotNull(message); assertFalse(message.trim().isEmpty()); errors.add(message); }
    }
    private static final class Factory implements SubtitleBatch.ReaderFactory {
        boolean available = true;
        final List<Reader> readers = new ArrayList<>();
        @Override public boolean isModelAvailable() { return available; }
        @Override public SubtitleBatch.Reader create() { Reader reader = new Reader(); readers.add(reader); return reader; }
    }
    private static final class Reader implements SubtitleBatch.Reader {
        ClipTranscriber.Listener listener;
        Runnable idle;
        int closes;
        @Override public void transcribe(Uri uri, ClipTranscriber.Listener listener) { this.listener = listener; }
        @Override public void closeWhenIdle(Runnable idle) { closes++; this.idle = idle; }
        void complete(List<SubtitleCue> cues) { listener.onComplete(cues, 7); }
        void error() { listener.onError("Synthetic reader failure"); }
        void release() { assertNotNull("Reader must be asked to close first", idle); idle.run(); }
    }
    private static List<SubtitleCue> words() { return Arrays.asList(new SubtitleCue(0, 600, "Synthetic words")); }
    private static Take take(String name) { return new Take(Uri.parse("file:///synthetic/" + name + ".mp4"), name, name, "", 1000); }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void ui(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private static void drain() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
}
