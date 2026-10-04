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
    /** Immutable identity also rejects A→B→A configuration changes during an in-flight frame. */
    static final class Configuration {
        final String mode, target;
        Configuration(String mode, String target) { this.mode = mode; this.target = target; }
    }
    private volatile Configuration configuration = new Configuration("fashion", FramingTarget.SCENE_DEFAULT);
    private long lastFrame;
    private long lastCueAt;
    private String lastCue = "";

    public PoseCoach(Listener listener) {
        this.listener = listener;
        detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU).build());
    }

    public synchronized void setMode(String scene) {
        String mode = scene == null ? "fashion" : scene.toLowerCase(Locale.ROOT);
        Configuration current = configuration;
        if (!current.mode.equals(mode)) configuration = new Configuration(mode, current.target);
    }
    public synchronized void setFramingTarget(String target) {
        String normalized = FramingTarget.normalize(target);
        Configuration current = configuration;
        if (!current.target.equals(normalized)) configuration = new Configuration(current.mode, normalized);
    }
    public void setEnabled(boolean value) { enabled = value; }

    // Package-private metadata seams: no frame submission or invented detector output in policy tests.
    Configuration configurationSnapshot() { return configuration; }
    boolean acceptsConfiguration(Configuration submitted) {
        return !closed && enabled && configuration == submitted
                && FramingTarget.requiresPerson(submitted.target, submitted.mode, "");
    }

    @Override public void analyze(ImageProxy frame) {
        long now = SystemClock.elapsedRealtime();
        final Configuration submitted = configurationSnapshot();
        if (!acceptsConfiguration(submitted) || now - lastFrame < 750 || !busy.compareAndSet(false, true)) {
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
                        if (acceptsConfiguration(submitted) && latency <= 2000) report(pose, geometry, latency, submitted);
                    })
                    .addOnFailureListener(error -> {
                        if (acceptsConfiguration(submitted)) emit("Framing coach unavailable. Use the preview to check your framing.", 0,
                                SystemClock.elapsedRealtime() - now, submitted);
                    })
                    .addOnCompleteListener(task -> { frame.close(); busy.set(false); });
        } catch (RuntimeException error) {
            frame.close();
            busy.set(false);
            if (acceptsConfiguration(submitted)) emit("Framing coach unavailable. Use the preview to check your framing.", 0, 0, submitted);
        }
    }

    private void report(Pose pose, FramingGeometry geometry, long latency, Configuration submitted) {
        int visible = 0;
        for (PoseLandmark point : pose.getAllPoseLandmarks()) if (inFrame(point, geometry)) visible++;
        PoseLandmark nose = pose.getPoseLandmark(PoseLandmark.NOSE);
        PoseLandmark left = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark right = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        if (!inFrame(nose, geometry) || !inFrame(left, geometry) || !inFrame(right, geometry)) {
            emit("I can't confidently see your face and shoulders. Check the preview and lighting.", visible, latency, submitted);
            return;
        }
        float center = (geometry.normalizeX(left.getPosition().x) + geometry.normalizeX(right.getPosition().x)) / 2f;
        float headY = geometry.normalizeY(nose.getPosition().y);
        float span = Math.abs(left.getPosition().x - right.getPosition().x) / geometry.getWidth();
        String cue;
        if (center < .25f || center > .75f) cue = "Bring your upper body closer to the center of the frame.";
        else if (headY < .08f) cue = "Leave a little more room above your head.";
        else if (span > .72f) cue = "Your shoulders are near the frame edges. Widen the framing if you want more room.";
        else if (FramingTarget.requestsFullOutfit(submitted.target, submitted.mode) &&
                (!inFrame(pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE), geometry) ||
                 !inFrame(pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE), geometry)))
            cue = "For a full outfit shot, check that your shoes fit in the preview.";
        else cue = "Face and shoulders detected. Check the preview, relax, and hold your pose.";
        emit(cue, visible, latency, submitted);
    }

    private static boolean inFrame(PoseLandmark point, FramingGeometry geometry) {
        return point != null && point.getInFrameLikelihood() >= .65f &&
                geometry.contains(point.getPosition().x, point.getPosition().y);
    }

    private synchronized void emit(String cue, int count, long latency, Configuration submitted) {
        if (!acceptsConfiguration(submitted)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastCueAt < 7000 || cue.equals(lastCue) && now - lastCueAt < 16000) return;
        lastCue = cue;
        lastCueAt = now;
        listener.onCue(cue, count, latency);
    }

    public void close() { closed = true; enabled = false; detector.close(); }
}
