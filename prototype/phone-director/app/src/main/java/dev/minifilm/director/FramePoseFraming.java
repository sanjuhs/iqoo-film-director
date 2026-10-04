package dev.minifilm.director;

import android.graphics.Bitmap;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local single-frame landmark evidence, not learned framing taste or a calibrated classifier.
 * ML Kit may extrapolate absent joints: only confident coordinates inside these pixels count.
 */
public final class FramePoseFraming {
    private static final float MIN_LIKELIHOOD = .75f;
    private static final String PROVENANCE = "ML Kit single-image pose-landmark heuristic (CPU preference); likelihood>=0.75 and in-bounds pixels; uncalibrated";
    private static final Gate PRODUCTION_GATE = new Gate();
    private static final Factory PRODUCTION_FACTORY = () -> {
        PoseDetector detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU).build());
        return new Client() {
            public Task<Pose> process(Bitmap bitmap) { return detector.process(InputImage.fromBitmap(bitmap, 0)); }
            public void close() { detector.close(); }
        };
    };
    private FramePoseFraming() {}

    /** Per-call synthetic seam; production never replaces its backend or shared gate. */
    interface Client { Task<Pose> process(Bitmap bitmap); void close(); }
    interface Factory { Client create(); }
    static final class Gate {
        private boolean occupied;
        // Retain task-owned resources even when caller cancellation/timeout has already returned.
        private Pending owner;
        synchronized int availablePermits() { return occupied ? 0 : 1; }
        private synchronized boolean acquire() { if (occupied) return false; occupied = true; return true; }
        private synchronized void own(Pending pending) { owner = pending; }
        private synchronized void release(Pending pending) {
            if (owner == pending) { owner = null; occupied = false; }
        }
    }
    private static final class Pending {
        final Gate gate;
        final AtomicBoolean cleanupStarted = new AtomicBoolean();
        Bitmap bitmap;
        Client client;
        Task<Pose> task;
        Pending(Gate gate) { this.gate = gate; gate.own(this); }
        void cleanup() {
            if (!cleanupStarted.compareAndSet(false, true)) return;
            boolean released = true;
            try { if (bitmap != null) bitmap.recycle(); } catch (RuntimeException failure) { released = false; }
            try { if (client != null) client.close(); } catch (RuntimeException | LinkageError failure) { released = false; }
            // A failed cleanup retains this owner and closes admission; no false reuse claim.
            if (released) gate.release(this);
        }
    }

    public static final class Result {
        public final String label, provenance;
        /** Aggregate visible joint groups and decision reason; no media or coordinates. */
        public final String reason;
        /** Minimum required landmark likelihood for a supported label, not framing accuracy. */
        public final float confidence;
        public final int visibleLandmarks;
        private Result(String label, String provenance, float confidence, int visibleLandmarks, String reason) {
            this.label = label; this.provenance = provenance;
            this.confidence = confidence; this.visibleLandmarks = visibleLandmarks;
            this.reason = reason;
        }
    }

    /** Worker only. RGB is upright, packed RGB888, no side above 512 pixels.
     * Cancel/timeout returns review needed promptly while pending task owns its image until completion.
     */
    public static Result inspect(byte[] rgb, int width, int height, BooleanSupplier cancelled) {
        return inspectWith(rgb, width, height, cancelled, PRODUCTION_FACTORY, PRODUCTION_GATE, 5000);
    }

    /** Worker-only bounded seam. The budget includes admission, preparation and task waiting. */
    static Result inspectWith(byte[] rgb, int width, int height, BooleanSupplier cancelled,
            Factory factory, Gate gate, long waitBudgetMs) {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Inspect reference framing on a worker thread.");
        if (width < 1 || height < 1 || width > 512 || height > 512 || rgb == null || rgb.length != width * height * 3)
            throw new IllegalArgumentException("Use a bounded upright RGB frame.");
        if (factory == null || gate == null || waitBudgetMs < 1 || waitBudgetMs > 5000)
            throw new IllegalArgumentException("Use a bounded local pose wait.");
        if (cancelled != null && cancelled.getAsBoolean()) return unavailable("cancelled before inference");
        long deadline = SystemClock.elapsedRealtime() + waitBudgetMs;
        try {
            while (!gate.acquire()) {
                if (cancelled != null && cancelled.getAsBoolean()) return unavailable("cancelled while waiting for earlier pose check");
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) return unavailable("earlier pose check still finishing; wait timed out");
                Thread.sleep(Math.min(100, remaining));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return unavailable("interrupted while waiting for earlier pose check");
        }
        Pending pending = new Pending(gate);
        try {
            if (cancelled != null && cancelled.getAsBoolean()) return unavailable("cancelled before inference");
            if (SystemClock.elapsedRealtime() >= deadline) return unavailable("pose wait timed out");
            int[] pixels = new int[width * height];
            for (int i = 0; i < pixels.length; i++) pixels[i] = 0xff000000 | (rgb[i * 3] & 255) << 16
                    | (rgb[i * 3 + 1] & 255) << 8 | (rgb[i * 3 + 2] & 255);
            pending.bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            pending.client = factory.create();
            if (pending.client == null) throw new IllegalStateException("Pose client unavailable");
            if (cancelled != null && cancelled.getAsBoolean()) return unavailable("cancelled before inference");
            if (SystemClock.elapsedRealtime() >= deadline) return unavailable("pose wait timed out");
            pending.task = pending.client.process(pending.bitmap);
            if (pending.task == null) throw new IllegalStateException("Pose task unavailable");
            // Non-Activity completion survives background/recreation. An already-complete Task
            // also schedules this listener. Only completion owns cleanup after submission.
            pending.task.addOnCompleteListener(Runnable::run, finished -> pending.cleanup());
            while (true) {
                if (cancelled != null && cancelled.getAsBoolean()) return unavailable("cancelled");
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) return unavailable("pose wait timed out");
                try {
                    Pose pose = Tasks.await(pending.task, Math.min(100, remaining), TimeUnit.MILLISECONDS);
                    return classify(pose, width, height);
                } catch (TimeoutException stillPending) { /* poll cancellation between bounded waits */ }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return unavailable("interrupted");
        } catch (Exception | LinkageError failure) {
            // If completion-listener registration failed, a completed Task is safe to clean;
            // otherwise retain the bounded owner rather than guessing native input is released.
            if (pending.task != null && pending.task.isComplete()) pending.cleanup();
            return unavailable("pose unavailable");
        } finally {
            if (pending.task == null) pending.cleanup();
        }
    }

    private static Result unavailable(String reason) { return new Result("review needed", PROVENANCE + "; " + reason, 0, 0, reason); }

    private static Result classify(Pose pose, int width, int height) {
        int visible = 0;
        for (PoseLandmark point : pose.getAllPoseLandmarks()) if (visible(point, width, height)) visible++;
        if (pose.getAllPoseLandmarks().isEmpty())
            return new Result("empty or unclear", PROVENANCE, 0, 0, "upper=0/3 hips=0/2 lower=0/4; no pose landmarks");
        int[] upper = {PoseLandmark.NOSE, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER};
        int[] hips = {PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP};
        int[] lower = {PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE, PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE};
        int upperCount = count(pose, upper, width, height), hipCount = count(pose, hips, width, height);
        int lowerCount = count(pose, lower, width, height);
        String groups = "upper=" + upperCount + "/3 hips=" + hipCount + "/2 lower=" + lowerCount + "/4; ";
        if (upperCount != upper.length)
            return new Result("review needed", PROVENANCE, 0, visible, groups + "upper evidence incomplete");
        if (hipCount == hips.length && lowerCount == lower.length)
            return new Result("full-body", PROVENANCE, minimum(pose, upper, hips, lower), visible, groups + "all required joints visible");
        if (hipCount == hips.length && lowerCount == 0)
            return new Result("waist-up", PROVENANCE, minimum(pose, upper, hips), visible, groups + "upper and hips visible; lower absent");
        if (hipCount == 0 && lowerCount == 0)
            return new Result("head-and-shoulders", PROVENANCE, minimum(pose, upper), visible, groups + "upper visible; hips and lower absent");
        return new Result("review needed", PROVENANCE, 0, visible, groups + "partial hip or lower evidence");
    }

    private static boolean visible(PoseLandmark point, int width, int height) {
        if (point == null || !Float.isFinite(point.getInFrameLikelihood())
                || point.getInFrameLikelihood() < MIN_LIKELIHOOD) return false;
        float x = point.getPosition().x, y = point.getPosition().y;
        return Float.isFinite(x) && Float.isFinite(y) && x >= 0 && x < width && y >= 0 && y < height;
    }
    private static int count(Pose pose, int[] types, int width, int height) {
        int count = 0;
        for (int type : types) if (visible(pose.getPoseLandmark(type), width, height)) count++;
        return count;
    }
    private static float minimum(Pose pose, int[]... groups) {
        float value = 1;
        for (int[] group : groups) for (int type : group)
            value = Math.min(value, pose.getPoseLandmark(type).getInFrameLikelihood());
        return value;
    }
}
