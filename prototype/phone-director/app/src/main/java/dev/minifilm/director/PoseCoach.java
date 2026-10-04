package dev.minifilm.director;

import android.media.Image;
import android.os.SystemClock;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bundled local pose inference followed by conservative, disclosed framing rules. */
public final class PoseCoach implements ImageAnalysis.Analyzer {
    public interface Listener { void onCue(String cue, int visibleLandmarks, long latencyMs); }
    private final Listener listener;
    private final PoseDetector detector;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean enabled = true;
    private volatile boolean closed;
    private volatile String mode = "fashion";
    private long lastFrame;
    private long lastCueAt;
    private String lastCue = "";

    public PoseCoach(Listener listener) {
        this.listener = listener;
        detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU).build());
    }

    public void setMode(String scene) { mode = scene == null ? "fashion" : scene.toLowerCase(Locale.ROOT); }
    public void setEnabled(boolean value) { enabled = value; }

    @Override public void analyze(ImageProxy frame) {
        long now = SystemClock.elapsedRealtime();
        if (!enabled || closed || now - lastFrame < 750 || !busy.compareAndSet(false, true)) {
            frame.close();
            return;
        }
        lastFrame = now;
        Image media = frame.getImage();
        if (media == null) { busy.set(false); frame.close(); return; }
        int rotation = frame.getImageInfo().getRotationDegrees();
        try {
            FramingGeometry geometry = new FramingGeometry(frame.getWidth(), frame.getHeight(),
                    frame.getCropRect(), rotation);
            detector.process(InputImage.fromMediaImage(media, rotation))
                    .addOnSuccessListener(pose -> {
                        long latency = SystemClock.elapsedRealtime() - now;
                        if (!closed && enabled && latency <= 2000) report(pose, geometry, latency);
                    })
                    .addOnFailureListener(error -> emit("Framing coach unavailable. Use the preview to check your framing.", 0,
                            SystemClock.elapsedRealtime() - now))
                    .addOnCompleteListener(task -> { frame.close(); busy.set(false); });
        } catch (RuntimeException error) {
            frame.close();
            busy.set(false);
            emit("Framing coach unavailable. Use the preview to check your framing.", 0, 0);
        }
    }

    private void report(Pose pose, FramingGeometry geometry, long latency) {
        int visible = 0;
        for (PoseLandmark point : pose.getAllPoseLandmarks()) if (inFrame(point, geometry)) visible++;
        PoseLandmark nose = pose.getPoseLandmark(PoseLandmark.NOSE);
        PoseLandmark left = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark right = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        if (!inFrame(nose, geometry) || !inFrame(left, geometry) || !inFrame(right, geometry)) {
            emit("I can't confidently see your face and shoulders. Check the preview and lighting.", visible, latency);
            return;
        }
        float center = (geometry.normalizeX(left.getPosition().x) + geometry.normalizeX(right.getPosition().x)) / 2f;
        float headY = geometry.normalizeY(nose.getPosition().y);
        float span = Math.abs(left.getPosition().x - right.getPosition().x) / geometry.getWidth();
        String cue;
        if (center < .25f || center > .75f) cue = "Bring your upper body closer to the center of the frame.";
        else if (headY < .08f) cue = "Leave a little more room above your head.";
        else if (span > .72f) cue = "Your shoulders are near the frame edges. Widen the framing if you want more room.";
        else if (mode.contains("fashion") &&
                (!inFrame(pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE), geometry) ||
                 !inFrame(pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE), geometry)))
            cue = "For a full outfit shot, check that your shoes fit in the preview.";
        else cue = "Face and shoulders detected. Check the preview, relax, and hold your pose.";
        emit(cue, visible, latency);
    }

    private static boolean inFrame(PoseLandmark point, FramingGeometry geometry) {
        return point != null && point.getInFrameLikelihood() >= .65f &&
                geometry.contains(point.getPosition().x, point.getPosition().y);
    }

    private void emit(String cue, int count, long latency) {
        if (closed || !enabled) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastCueAt < 7000 || cue.equals(lastCue) && now - lastCueAt < 16000) return;
        lastCue = cue;
        lastCueAt = now;
        listener.onCue(cue, count, latency);
    }

    public void close() { closed = true; enabled = false; detector.close(); }
}
