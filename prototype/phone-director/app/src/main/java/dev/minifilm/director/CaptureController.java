package dev.minifilm.director;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Looper;
import android.util.Rational;
import android.util.Size;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground-only phone capture for the dated research prototype.
 * Preview and recording require separate user actions. All methods except the
 * state getters run on the UI thread. The analyzer owns ImageProxy.close().
 */
public final class CaptureController implements AutoCloseable {
    public interface Listener {
        void onReady();
        void onRecordingStarted();
        void onRecordingFinished(Uri uri, long durationMs);
        void onError(String message);
    }

    private final ComponentActivity activity;
    private final PreviewView previewView;
    private final Listener listener;
    private final Executor mainExecutor;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider provider;
    private VideoCapture<Recorder> videoCapture;
    private ImageAnalysis analysis;
    private ImageAnalysis.Analyzer analyzer;
    private volatile Recording recording;
    private boolean stopRequested;
    private boolean previewRequested;
    private boolean ready;
    private boolean closed;
    private volatile boolean analysisAvailable;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private int bindingGeneration;

    public CaptureController(ComponentActivity activity, PreviewView preview, Listener listener) {
        this.activity = activity;
        this.previewView = preview;
        this.listener = listener;
        this.mainExecutor = ContextCompat.getMainExecutor(activity);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
    }

    public void setAnalyzer(ImageAnalysis.Analyzer analyzer) {
        requireMainThread();
        this.analyzer = analyzer;
        if (analysis != null) attachAnalyzer();
    }

    public boolean isAnalysisAvailable() { return analysisAvailable; }

    public int getLensFacing() { return lensFacing; }

    /** Select a lens before explicit preview, or rebind the active visible preview. */
    public void setLensFacing(int facing) {
        requireMainThread();
        if (facing != CameraSelector.LENS_FACING_FRONT && facing != CameraSelector.LENS_FACING_BACK)
            throw new IllegalArgumentException("Choose the front or back phone camera");
        if (closed || facing == lensFacing) return;
        if (isRecording()) {
            listener.onError("Finish this take before switching cameras.");
            return;
        }
        lensFacing = facing;
        if (previewRequested) startPreview();
    }

    public void startPreview() {
        requireMainThread();
        if (closed) return;
        if (isRecording()) {
            listener.onError("Finish this take before restarting camera preview.");
            return;
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            listener.onError("Allow camera access to start your shoot preview.");
            return;
        }
        previewRequested = true;
        ready = false;
        int generation = ++bindingGeneration;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            if (closed || !previewRequested || generation != bindingGeneration) return;
            try {
                provider = future.get();
                bindCamera();
            } catch (Exception exception) {
                ready = false;
                listener.onError("Camera could not open. Close other camera apps and try again.");
            }
        }, mainExecutor);
    }

    private void bindCamera() throws Exception {
        if (closed || !previewRequested || !hasPermission(Manifest.permission.CAMERA)) return;
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        if (!provider.hasCamera(selector)) {
            lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                    ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
            if (!provider.hasCamera(selector)) throw new IllegalStateException("No usable camera");
        }
        provider.unbindAll();
        int rotation = previewView.getDisplay() == null ? 0 : previewView.getDisplay().getRotation();
        Preview preview = new Preview.Builder().setTargetRotation(rotation).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        Recorder recorder = new Recorder.Builder().setQualitySelector(
                QualitySelector.from(Quality.HD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build();
        videoCapture = VideoCapture.withOutput(recorder);
        videoCapture.setTargetRotation(rotation);
        analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(480, 640))
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        attachAnalyzer();
        // Same sensor crop for the phone's portrait reel, visible preview and pose frames.
        ViewPort viewport = new ViewPort.Builder(new Rational(9, 16), rotation)
                .setScaleType(ViewPort.FILL_CENTER).build();
        try {
            UseCaseGroup group = new UseCaseGroup.Builder().setViewPort(viewport)
                    .addUseCase(preview).addUseCase(videoCapture).addUseCase(analysis).build();
            provider.bindToLifecycle(activity, selector, group);
            analysisAvailable = true;
        } catch (IllegalArgumentException unsupportedCombination) {
            // Some cameras cannot provide video, preview and analysis together.
            // Keep real capture usable while honestly disabling live AI cues.
            provider.unbindAll();
            analysis.clearAnalyzer();
            analysis = null;
            analysisAvailable = false;
            UseCaseGroup group = new UseCaseGroup.Builder().setViewPort(viewport)
                    .addUseCase(preview).addUseCase(videoCapture).build();
            provider.bindToLifecycle(activity, selector, group);
        }
        ready = true;
        listener.onReady();
    }

    private void attachAnalyzer() {
        ImageAnalysis.Analyzer currentAnalyzer = analyzer;
        analysis.setAnalyzer(analysisExecutor, image -> {
            if (currentAnalyzer == null) {
                image.close();
                return;
            }
            try {
                currentAnalyzer.analyze(image);
            } catch (RuntimeException failure) {
                image.close();
                // The analyzer reports semantic/ML errors; keep capture alive.
            }
        });
    }

    public void switchCamera() {
        requireMainThread();
        if (closed || !previewRequested) return;
        if (isRecording()) {
            listener.onError("Finish this take before switching cameras.");
            return;
        }
        setLensFacing(lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT);
    }

    public void startRecording() {
        requireMainThread();
        if (closed || isRecording()) return;
        if (!ready || !previewRequested || videoCapture == null) {
            listener.onError("Start camera preview before recording a take.");
            return;
        }
        if (!activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            listener.onError("Keep the shoot screen visible while recording.");
            return;
        }
        if (!hasPermission(Manifest.permission.CAMERA)
                || !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            listener.onError("Allow camera and microphone access before recording.");
            return;
        }
        File directory = new File(activity.getFilesDir(), "takes");
        if ((!directory.isDirectory() && !directory.mkdirs())
                || activity.getFilesDir().getUsableSpace() < 256L * 1024L * 1024L) {
            listener.onError("Free some phone storage before recording a take.");
            return;
        }
        File outputFile = new File(directory, "take-" + UUID.randomUUID() + ".mp4");
        FileOutputOptions options = new FileOutputOptions.Builder(outputFile)
                .setFileSizeLimit(100L * 1024L * 1024L)
                .setDurationLimitMillis(60_000L).build();
        stopRequested = false;
        try {
            recording = videoCapture.getOutput().prepareRecording(activity, options)
                    .withAudioEnabled().start(mainExecutor, event -> handleEvent(event, outputFile));
        } catch (RuntimeException failure) {
            recording = null;
            if (outputFile.exists()) outputFile.delete();
            listener.onError("Recording could not start. Check camera and microphone access.");
        }
    }

    private void handleEvent(VideoRecordEvent event, File file) {
        if (event instanceof VideoRecordEvent.Start) {
            if (!closed && previewRequested && !stopRequested) listener.onRecordingStarted();
        } else if (event instanceof VideoRecordEvent.Finalize) {
            recording = null;
            stopRequested = false;
            VideoRecordEvent.Finalize result = (VideoRecordEvent.Finalize) event;
            boolean usableResult = !result.hasError()
                    || result.getError() == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                    || result.getError() == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                    || result.getError() == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE;
            // Synchronous on Finalize's UI callback: validate before the listener can
            // advance/rebind. Container duration is authoritative for editor/ASR bounds;
            // CameraX RecordingStats can differ at codec/container edges.
            long durationMs = usableResult ? finalizedVideoDuration(file) : 0;
            if (usableResult && durationMs > 0 && file.length() > 0) {
                if (!closed) listener.onRecordingFinished(Uri.fromFile(file), durationMs);
            } else {
                // Only this controller's failed new file is removed; originals remain intact.
                if (file.exists()) file.delete();
                if (!closed) listener.onError("This take could not be saved (camera error "
                        + result.getError() + "). Please try again.");
            }
        }
    }

    /** Read a finalized local video, never substitute recording statistics on failure.
     * Returns zero for an empty, unreadable, non-video or malformed new recording.
     */
    static long finalizedVideoDuration(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return 0;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(file.getAbsolutePath());
            if (!"yes".equalsIgnoreCase(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))
                return 0;
            int width = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            long duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            return width > 0 && height > 0 && duration > 0 ? duration : 0;
        } catch (RuntimeException unreadable) {
            return 0;
        } finally {
            try { metadata.release(); } catch (Exception ignored) { }
        }
    }

    public void stopRecording() {
        requireMainThread();
        if (recording != null && !stopRequested) {
            stopRequested = true;
            recording.stop();
        }
    }

    /** Includes the interval while CameraX is flushing the previous take. */
    public boolean isRecording() { return recording != null; }

    /** Call from Activity.onStop(); reopening still requires the creator's Preview action. */
    public void stopPreview() {
        requireMainThread();
        previewRequested = false;
        ready = false;
        analysisAvailable = false;
        ++bindingGeneration;
        stopRecording();
        if (analysis != null) analysis.clearAnalyzer();
        if (provider != null) provider.unbindAll();
        analysis = null;
        videoCapture = null;
    }

    /** Call once from Activity.onDestroy(), after stopping the visible shoot. */
    @Override public void close() {
        requireMainThread();
        if (closed) return;
        stopPreview();
        closed = true;
        analysisExecutor.shutdown();
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("CaptureController must be called from the UI thread");
        }
    }
}
