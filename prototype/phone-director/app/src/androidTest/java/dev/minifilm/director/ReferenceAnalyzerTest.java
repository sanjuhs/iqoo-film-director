package dev.minifilm.director;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Synthetic media only: no camera, microphone, web reference, or private recording. */
@RunWith(AndroidJUnit4.class)
public final class ReferenceAnalyzerTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 90_000) public void flatColorReferenceDoesNotInventCutsPeopleOrGarmentSemantics() throws Exception {
        File fixture = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Parent installs the explicitly synthetic flat-color spoken fixture", fixture.isFile());
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals("Inspection app must not have network permission", "android.permission.INTERNET", permission);
        Result result = inspect(Uri.fromFile(fixture));
        assertTrue(result.summary, result.summary.contains("0 change candidates, approx 1 visual beats"));
        assertTrue(result.summary, result.summary.contains("person detected in 0/6 pose samples"));
        assertTrue(result.summary, result.summary.contains("non-cut frame-change 0.000"));
        // The fixture's name and audio mention a jacket; visual pixels cannot establish that object.
        assertFalse(result.summary, result.summary.toLowerCase(java.util.Locale.US).contains("jacket"));
        assertFalse(result.summary, result.summary.toLowerCase(java.util.Locale.US).contains("fashion"));
        assertTrue(result.summary.length() <= 300);
        assertTrue(result.summary.contains("Sparse heuristics; no object/style recognition"));
        Log.i("MiniFilmReferenceTest", "REFERENCE_FLAT_PASS synthetic=true false_cuts=0 detected_people_samples=0"
                + " semantic_fabrication=false elapsed_ms=" + result.elapsed + " summary=" + result.summary);
    }

    @Test(timeout = 180_000) public void threeSyntheticScenesProduceTwoApproximateChangeCandidates() throws Exception {
        CountDownLatch generated = new CountDownLatch(1);
        AtomicReference<List<Take>> clips = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        DemoAssets.create(context, new DemoAssets.Listener() {
            public void onReady(List<Take> takes) { clips.set(takes); generated.countDown(); }
            public void onError(String message) { error.set(message); generated.countDown(); }
        });
        assertTrue(generated.await(60, TimeUnit.SECONDS)); assertNull(error.get(), error.get());
        CountDownLatch exported = new CountDownLatch(1);
        AtomicReference<Uri> sequence = new AtomicReference<>();
        ReelExporter exporter = new ReelExporter(context);
        new Handler(Looper.getMainLooper()).post(() -> exporter.export(clips.get(),
                "Synthetic reference boundary test", "Clean", new ReelExporter.Listener() {
            public void onProgress(int percent) { }
            public void onComplete(Uri video, Uri edit) { sequence.set(video); exported.countDown(); }
            public void onError(String message) { error.set(message); exported.countDown(); }
        }));
        try {
            assertTrue("Synthetic export timed out", exported.await(90, TimeUnit.SECONDS));
            assertNull(error.get(), error.get()); assertNotNull(sequence.get());
            Result result = inspect(sequence.get());
            assertTrue(result.summary, result.summary.contains("2 change candidates, approx 3 visual beats"));
            assertTrue(result.summary, result.summary.contains("person detected in 0/6 pose samples"));
            assertTrue(result.summary.length() <= 300);
            Log.i("MiniFilmReferenceTest", "REFERENCE_CUTS_PASS synthetic=true expected_changes=2"
                    + " detected_people_samples=0 elapsed_ms=" + result.elapsed + " summary=" + result.summary);
        } finally { new Handler(Looper.getMainLooper()).post(exporter::cancel); }
    }

    @Test(timeout = 10_000) public void rejectsWebReferenceWithoutFetchingIt() throws Exception {
        ReferenceAnalyzer analyzer = new ReferenceAnalyzer(context);
        CountDownLatch done = new CountDownLatch(1); AtomicReference<String> result = new AtomicReference<>();
        try {
            analyzer.analyze(Uri.parse("https://example.invalid/reference.mp4"), new ReferenceAnalyzer.Listener() {
                public void onResult(String summary, long elapsedMs) { result.set("Unexpected web result"); done.countDown(); }
                public void onError(String message) { result.set(message); done.countDown(); }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(result.get(), result.get().contains("local reference video"));
        } finally { analyzer.close(); }
    }

    private Result inspect(Uri uri) throws Exception {
        ReferenceAnalyzer analyzer = new ReferenceAnalyzer(context);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Result> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        try {
            analyzer.analyze(uri, new ReferenceAnalyzer.Listener() {
                public void onResult(String summary, long elapsedMs) { result.set(new Result(summary, elapsedMs)); done.countDown(); }
                public void onError(String message) { error.set(message); done.countDown(); }
            });
            assertTrue("Reference inspection timed out", done.await(65, TimeUnit.SECONDS));
            assertNull(error.get(), error.get()); assertNotNull(result.get()); return result.get();
        } finally { analyzer.close(); }
    }
    private static final class Result {
        final String summary; final long elapsed;
        Result(String summary, long elapsed) { this.summary = summary; this.elapsed = elapsed; }
    }
}
