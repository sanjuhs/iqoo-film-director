package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit local video selection only; returns one review frame, never opens the camera. */
public final class ReferenceFrameDecoder implements AutoCloseable {
    public interface Listener {
        /** Receiver owns and must recycle this frame. Timestamp is the requested seek, not exact decoded PTS. */
        void onFrame(Bitmap frame, long requestedMs, long durationMs);
        void onError(String message);
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    public ReferenceFrameDecoder(Context context) { this.context = context.getApplicationContext(); }
    public synchronized void decode(Uri uri, long requestedMs, Listener listener) {
        if (closed) return;
        worker.execute(() -> {
            if (closed) return;
            MediaMetadataRetriever reader = new MediaMetadataRetriever();
            Bitmap frame = null;
            try {
                if (uri == null || !("file".equals(uri.getScheme()) || "content".equals(uri.getScheme())))
                    throw new IllegalArgumentException("Choose a reference video saved on your phone.");
                if ("file".equals(uri.getScheme())) reader.setDataSource(uri.getPath());
                else reader.setDataSource(context, uri);
                if (!"yes".equals(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))
                    throw new IllegalArgumentException("This file has no video track.");
                long duration = Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                if (duration <= 0 || duration > 180_000 || requestedMs < 0 || requestedMs >= duration)
                    throw new IllegalArgumentException("Choose a time inside a local reference up to three minutes long.");
                if (closed) return;
                frame = reader.getScaledFrameAtTime(requestedMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST, 512, 512);
                if (frame == null) throw new IllegalStateException("This reference frame could not be decoded.");
                Bitmap ready = frame; frame = null;
                main.post(() -> { if (closed) ready.recycle(); else listener.onFrame(ready, requestedMs, duration); });
            } catch (Exception failure) {
                String message = failure instanceof IllegalArgumentException || failure instanceof IllegalStateException
                        ? failure.getMessage() : "This local reference could not be read. Choose another video.";
                main.post(() -> { if (!closed) listener.onError(message); });
            } finally {
                if (frame != null) frame.recycle();
                try { reader.release(); } catch (Exception ignored) { }
            }
        });
    }
    @Override public synchronized void close() { closed = true; worker.shutdown(); }
}
