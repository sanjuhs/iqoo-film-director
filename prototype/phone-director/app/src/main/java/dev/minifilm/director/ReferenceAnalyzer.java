package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sparse local measurements, not semantic trend understanding or full-video analysis. */
public final class ReferenceAnalyzer implements AutoCloseable {
    public interface Listener {
        void onResult(String summary, long elapsedMs);
        void onError(String message);
    }
    private static final long MAX_DURATION_MS = 180_000;
    private static final int MAX_FRAMES = 24;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean closed;

    public ReferenceAnalyzer(Context context) { this.context = context.getApplicationContext(); }

    public synchronized void analyze(Uri uri, Listener listener) {
        if (closed) { main.post(() -> listener.onError("Reference inspector is closed.")); return; }
        if (uri == null || !("file".equals(uri.getScheme()) || "content".equals(uri.getScheme()))) {
            main.post(() -> { if (!closed) listener.onError("Choose a local reference video, not a web link."); });
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            main.post(() -> { if (!closed) listener.onError("A reference inspection is already running."); });
            return;
        }
        worker.execute(() -> inspect(uri, listener));
    }

    private void inspect(Uri uri, Listener listener) {
        long began = SystemClock.elapsedRealtime();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        PoseSamples pose = null;
        try {
            if (closed) return;
            if ("file".equals(uri.getScheme())) retriever.setDataSource(uri.getPath());
            else retriever.setDataSource(context, uri);
            if (!"yes".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))
                throw new IllegalArgumentException("This file has no video track.");
            long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (duration <= 0 || duration > MAX_DURATION_MS)
                throw new IllegalArgumentException("Choose a reference up to three minutes long; trim longer videos first.");
            int requested = (int) Math.min(MAX_FRAMES, Math.max(2, (duration + 749) / 750 + 1));
            boolean[] poseFrames = new boolean[requested];
            int plannedPose = Math.min(6, requested);
            for (int i = 0; i < plannedPose; i++)
                poseFrames[Math.round(i * (requested - 1f) / (plannedPose - 1f))] = true;
            try { pose = new PoseSamples(); } catch (RuntimeException unavailable) { /* Pixel observations still work. */ }
            Features previous = null;
            int sampled = 0, changes = 0, motionPairs = 0;
            double motionSum = 0, red = 0, green = 0, blue = 0;
            for (int i = 0; i < requested; i++) {
                if (closed) return;
                if (SystemClock.elapsedRealtime() - began > 45_000)
                    throw new IllegalStateException("Reference inspection timed out. Try a shorter local clip.");
                long timeMs = Math.round(i * Math.max(0, duration - 1d) / (requested - 1d));
                Bitmap frame = retriever.getScaledFrameAtTime(timeMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST, 480, 640);
                if (frame == null) continue;
                boolean retainedByPendingPose = false;
                try {
                    Features current = Features.from(frame);
                    red += current.red; green += current.green; blue += current.blue;
                    sampled++;
                    if (previous != null) {
                        double difference = current.pixelDifference(previous);
                        // RGB histogram shifts can also be caused by lighting or motion.
                        boolean candidate = current.histogramDifference(previous) >= .20;
                        if (candidate) changes++;
                        else { motionPairs++; motionSum += difference; }
                    }
                    previous = current;
                    if (pose != null && poseFrames[i]) retainedByPendingPose = pose.inspect(frame);
                } finally {
                    if (!retainedByPendingPose) frame.recycle();
                }
            }
            if (sampled < 2) throw new IllegalStateException("Not enough reference frames could be decoded.");
            String person = pose == null || pose.successful == 0 ? "pose unavailable"
                    : String.format(Locale.US, "person detected in %d/%d pose samples", pose.detected, pose.successful);
            if (pose != null && pose.failed > 0) person += " (partial)";
            String summary = String.format(Locale.US,
                    "Sampled %.1fs: %d frames; %d change candidates, approx %d visual beats; " +
                    "mean RGB %d/%d/%d; non-cut frame-change %.3f; %s. Sparse heuristics; no object/style recognition.",
                    duration / 1000d, sampled, changes, changes + 1,
                    Math.round(red / sampled), Math.round(green / sampled), Math.round(blue / sampled),
                    motionPairs == 0 ? 0 : motionSum / motionPairs, person);
            if (summary.length() > 300) summary = summary.substring(0, 300);
            final String result = summary;
            long elapsed = SystemClock.elapsedRealtime() - began;
            Log.i("MiniFilmReference", "REFERENCE_OK frames=" + sampled + " change_candidates=" + changes
                    + " pose_samples=" + (pose == null ? 0 : pose.successful)
                    + " detected_samples=" + (pose == null ? 0 : pose.detected)
                    + " elapsed_ms=" + elapsed + " backend_preference=pose_cpu sparse=true");
            main.post(() -> { if (!closed) listener.onResult(result, elapsed); });
        } catch (Exception failure) {
            String message = failure instanceof IllegalArgumentException || failure instanceof IllegalStateException
                    ? failure.getMessage() : "Reference could not be read. Choose a downloaded local video and try again.";
            final String error = message == null ? "Reference inspection failed." : message;
            main.post(() -> { if (!closed) listener.onError(error); });
        } finally {
            if (pose != null) pose.close();
            try { retriever.release(); } catch (Exception ignored) { }
            busy.set(false);
        }
    }

    @Override public synchronized void close() { closed = true; worker.shutdown(); }

    private static final class PoseSamples implements AutoCloseable {
        private final PoseDetector detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU).build());
        int successful, detected, failed;
        boolean timedOut;
        /** A timed-out detector still owns its Bitmap until its task completes. */
        boolean inspect(Bitmap frame) {
            if (timedOut) return false;
            Task<Pose> task = null;
            try {
                task = detector.process(InputImage.fromBitmap(frame, 0));
                Pose result = Tasks.await(task, 5, TimeUnit.SECONDS);
                successful++;
                if (visible(result.getPoseLandmark(PoseLandmark.NOSE), frame)
                        && visible(result.getPoseLandmark(PoseLandmark.LEFT_SHOULDER), frame)
                        && visible(result.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER), frame)) detected++;
            } catch (TimeoutException timeout) {
                failed++; timedOut = true;
                task.addOnCompleteListener(done -> { frame.recycle(); detector.close(); });
                return true;
            } catch (Exception failure) { failed++; }
            return false;
        }
        private static boolean visible(PoseLandmark point, Bitmap frame) {
            return point != null && point.getInFrameLikelihood() >= .65f
                    && point.getPosition().x >= 0 && point.getPosition().x <= frame.getWidth()
                    && point.getPosition().y >= 0 && point.getPosition().y <= frame.getHeight();
        }
        @Override public void close() { if (!timedOut) detector.close(); }
    }

    private static final class Features {
        final int[] grid = new int[32 * 32];
        final double[] histogram = new double[48];
        double red, green, blue;
        static Features from(Bitmap frame) {
            Features result = new Features();
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int color = frame.getPixel(Math.min(frame.getWidth() - 1, x * frame.getWidth() / 32),
                        Math.min(frame.getHeight() - 1, y * frame.getHeight() / 32));
                result.grid[y * 32 + x] = color;
                int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
                result.red += r; result.green += g; result.blue += b;
                result.histogram[r / 16] += 1d / result.grid.length;
                result.histogram[16 + g / 16] += 1d / result.grid.length;
                result.histogram[32 + b / 16] += 1d / result.grid.length;
            }
            result.red /= result.grid.length; result.green /= result.grid.length; result.blue /= result.grid.length;
            return result;
        }
        double histogramDifference(Features other) {
            double sum = 0;
            for (int i = 0; i < histogram.length; i++) sum += Math.abs(histogram[i] - other.histogram[i]);
            return sum / 6d;
        }
        double pixelDifference(Features other) {
            double sum = 0;
            for (int i = 0; i < grid.length; i++) {
                sum += Math.abs(Color.red(grid[i]) - Color.red(other.grid[i]));
                sum += Math.abs(Color.green(grid[i]) - Color.green(other.grid[i]));
                sum += Math.abs(Color.blue(grid[i]) - Color.blue(other.grid[i]));
            }
            return sum / (grid.length * 3d * 255d);
        }
    }
}
