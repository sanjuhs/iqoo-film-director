package dev.minifilm.director;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Synthetic local fixture, no Activity/UI, no autoplay, camera, microphone or picker. */
@RunWith(AndroidJUnit4.class)
public final class ClipPreviewTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test public void selectedRangeIsSourceRelativeAndOmittedRangePreservesFullPreview() throws Exception {
        Uri uri = fixture();
        MediaItem cut = PreviewActivity.previewItem(context, uri, 1000L, 2500L);
        assertEquals(uri, cut.localConfiguration.uri);
        assertEquals(1000, cut.clippingConfiguration.startPositionMs);
        assertEquals(2500, cut.clippingConfiguration.endPositionMs);
        assertFalse(cut.clippingConfiguration.relativeToDefaultPosition);
        MediaItem full = PreviewActivity.previewItem(context, uri, null, null);
        assertEquals(0, full.clippingConfiguration.startPositionMs);
        assertEquals(C.TIME_END_OF_SOURCE, full.clippingConfiguration.endPositionMs);
    }

    @Test public void invalidPairsAndBoundsRejectBeforePlayerCreation() throws Exception {
        for (Long[] range : new Long[][] {{null, 2000L}, {0L, null}, {-1L, 2000L},
                {2000L, 2000L}, {2001L, 2000L}, {0L, Long.MAX_VALUE}}) {
            try { PreviewActivity.validateRange(range[0], range[1], 5750); fail("Invalid range accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        Uri uri = fixture(); MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        long duration;
        try { metadata.setDataSource(context, uri); duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { metadata.release(); }
        assertEquals(duration, PreviewActivity.previewItem(context, uri, 0L, duration).clippingConfiguration.endPositionMs);
        try { PreviewActivity.previewItem(context, uri, 0L, duration + 1); fail("Out point beyond actual source was accepted"); }
        catch (IllegalArgumentException expected) { }
        try { PreviewActivity.previewItem(context, Uri.parse("https://example.invalid/synthetic.mp4"), null, null); fail("Remote source accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test(timeout = 60_000) public void selectedSyntheticRangePreparesPausedWithClippedDurationWithoutActivity() throws Exception {
        MediaItem item = PreviewActivity.previewItem(context, fixture(), 1000L, 2500L);
        AtomicReference<ExoPlayer> player = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1), released = new CountDownLatch(1);
        long[] duration = new long[1], position = new long[1];
        boolean[] playing = new boolean[1], autoplay = new boolean[1];
        main.post(() -> {
            try {
                ExoPlayer preview = new ExoPlayer.Builder(context).build(); player.set(preview);
                preview.setPlayWhenReady(false); preview.setVolume(0);
                preview.addListener(new Player.Listener() {
                    @Override public void onPlaybackStateChanged(int state) {
                        if (state != Player.STATE_READY) return;
                        duration[0] = preview.getDuration(); position[0] = preview.getCurrentPosition();
                        playing[0] = preview.isPlaying(); autoplay[0] = preview.getPlayWhenReady(); ready.countDown();
                    }
                    @Override public void onPlayerError(PlaybackException failure) {
                        error.set(failure.getErrorCodeName()); ready.countDown();
                    }
                });
                preview.setMediaItem(item); preview.prepare();
            } catch (RuntimeException failure) { error.set(failure.getClass().getSimpleName()); ready.countDown(); }
        });
        try {
            assertTrue("Headless clipped player did not prepare", ready.await(25, TimeUnit.SECONDS));
            assertNull(error.get(), error.get());
            assertEquals("Player timeline must be the selected source-relative range length", 1500, duration[0]);
            assertEquals("Paused cut starts at its own zero, corresponding to source 1000ms", 0, position[0]);
            assertFalse("Preview must not autoplay", autoplay[0]); assertFalse(playing[0]);
        } finally {
            main.post(() -> { if (player.get() != null) player.get().release(); released.countDown(); });
            assertTrue("Headless player did not release", released.await(5, TimeUnit.SECONDS));
        }
    }

    private Uri fixture() {
        File file = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Root must install the labelled synthetic spoken fixture", file.isFile());
        return Uri.fromFile(file);
    }
}
