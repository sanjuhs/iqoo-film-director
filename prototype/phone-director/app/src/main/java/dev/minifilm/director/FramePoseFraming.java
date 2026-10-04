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

/** Local single-frame landmark evidence, not learned framing taste or a calibrated classifier.
 * ML Kit may extrapolate absent joints: only confident coordinates inside these pixels count.
 */
public final class FramePoseFraming {
    private static final float MIN_LIKELIHOOD = .75f;
    private static final String PROVENANCE = "ML Kit single-image pose-landmark heuristic (CPU preference); likelihood>=0.75 and in-bounds pixels; uncalibrated";
    private FramePoseFraming() {}

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
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Inspect reference framing on a worker thread.");
        if (width < 1 || height < 1 || width > 512 || height > 512 || rgb == null || rgb.length != width * height * 3)
            throw new IllegalArgumentException("Use a bounded upright RGB frame.");
        if (cancelled != null && cancelled.getAsBoolean()) return unavailable("cancelled before inference");
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0xff000000 | (rgb[i * 3] & 255) << 16
                | (rgb[i * 3 + 1] & 255) << 8 | (rgb[i * 3 + 2] & 255);
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        PoseDetector detector = null;
        Task<Pose> task;
        try {
            detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                    .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                    .setPreferredHardwareConfigs(PoseDetectorOptions.CPU).build());
            task = detector.process(InputImage.fromBitmap(bitmap, 0));
        } catch (RuntimeException failure) {
            bitmap.recycle(); if (detector != null) detector.close();
            return unavailable("pose unavailable");
        }
        final PoseDetector activeDetector = detector;
        // Attaching before waiting also covers a task that finishes during cancellation/timeout.
        // The Pose result itself contains coordinates, so classification needs no bitmap after completion.
        task.addOnCompleteListener(Runnable::run, finished -> {
            bitmap.recycle(); activeDetector.close();
        });
        long deadline = SystemClock.elapsedRealtime() + 5000;
        try {
            while (true) {
                if (cancelled != null && cancelled.getAsBoolean()) return unavailable("cancelled");
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) return unavailable("pose wait timed out");
                try {
                    Pose pose = Tasks.await(task, Math.min(100, remaining), TimeUnit.MILLISECONDS);
                    return classify(pose, width, height);
                } catch (TimeoutException stillPending) { /* poll cancellation between bounded waits */ }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return unavailable("interrupted");
        } catch (Exception failure) { return unavailable("pose unavailable"); }
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
