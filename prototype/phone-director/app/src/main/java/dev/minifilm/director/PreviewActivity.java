package dev.minifilm.director;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/** Foreground-only local playback. Opening a preview never starts audio automatically. */
public final class PreviewActivity extends ComponentActivity {
    /** Optional source-relative cut range; provide both extras or neither. */
    public static final String EXTRA_START_MS = "preview.startMs";
    public static final String EXTRA_END_MS = "preview.endMs";
    /** Geometry-only center crop for source take review; exported reels use the default FIT mode. */
    public static final String EXTRA_CROP_TO_REEL = "preview.cropToReel";
    private static final String POSITION = "preview.positionMs";
    private static final String SOURCE = "preview.source";
    private PlayerView playerView;
    private ExoPlayer player;
    private ProgressBar loading;
    private TextView error;
    private Uri source;
    private long positionMs;
    private Long startMs, endMs;
    private boolean validRange = true;
    private boolean cropToReel;
    private FrameLayout cropViewport;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        source = getIntent() == null ? null : getIntent().getData();
        cropToReel = getIntent() != null && getIntent().getBooleanExtra(EXTRA_CROP_TO_REEL, false);
        if (getIntent() != null) {
            boolean hasStart = getIntent().hasExtra(EXTRA_START_MS), hasEnd = getIntent().hasExtra(EXTRA_END_MS);
            try {
                startMs = hasStart ? getIntent().getLongExtra(EXTRA_START_MS, Long.MIN_VALUE) : null;
                endMs = hasEnd ? getIntent().getLongExtra(EXTRA_END_MS, Long.MIN_VALUE) : null;
                validateRange(startMs, endMs, -1);
            } catch (RuntimeException invalid) { validRange = false; }
        }
        if (state != null) {
            String savedSource = state.getString(SOURCE);
            if (source == null && savedSource != null) source = Uri.parse(savedSource);
            boolean sameRange = (startMs == null ? !state.containsKey(EXTRA_START_MS)
                    : state.containsKey(EXTRA_START_MS) && startMs == state.getLong(EXTRA_START_MS))
                    && (endMs == null ? !state.containsKey(EXTRA_END_MS)
                    : state.containsKey(EXTRA_END_MS) && endMs == state.getLong(EXTRA_END_MS));
            if (source != null && source.toString().equals(savedSource) && sameRange)
                positionMs = Math.max(0, state.getLong(POSITION));
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setResizeMode(cropToReel ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(3000);
        playerView.setControllerAutoShow(true);
        playerView.setShowPreviousButton(false);
        playerView.setShowNextButton(false);
        if (cropToReel) {
            playerView.setShowFastForwardButton(false);
            playerView.setShowRewindButton(false);
        }
        if (cropToReel) {
            // The video zooms inside a separate 9:16 clip. Controls remain inside that
            // viewport; fitting to the whole phone would crop to the phone's own aspect.
            LinearLayout review = new LinearLayout(this);
            review.setOrientation(LinearLayout.VERTICAL);
            review.setPadding(dp(12), dp(94), dp(12), dp(12));
            TextView label = new TextView(this);
            label.setText("Reel crop preview · framing only\nSource audio is unchanged. Export text and color are not shown.");
            label.setTextColor(0xFFF5F5EF); label.setTextSize(12);
            label.setGravity(Gravity.CENTER); label.setPadding(dp(4), 0, dp(4), dp(10));
            review.addView(label, new LinearLayout.LayoutParams(-1, -2));
            FrameLayout available = new FrameLayout(this);
            available.setClipChildren(true); available.setClipToPadding(true);
            review.addView(available, new LinearLayout.LayoutParams(-1, 0, 1));
            cropViewport = new FrameLayout(this);
            cropViewport.setClipChildren(true); cropViewport.setClipToPadding(true);
            cropViewport.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
            available.addView(cropViewport, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
            available.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB) -> {
                int width = r-l, height = b-t;
                if (width <= 0 || height <= 0) return;
                int fittedWidth = Math.min(width, Math.max(1, Math.round(height * 9f/16f)));
                int fittedHeight = Math.min(height, Math.max(1, Math.round(fittedWidth * 16f/9f)));
                FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) cropViewport.getLayoutParams();
                if (bounds.width != fittedWidth || bounds.height != fittedHeight) {
                    bounds.width = fittedWidth; bounds.height = fittedHeight;
                    cropViewport.setLayoutParams(bounds);
                }
            });
            root.addView(review, new FrameLayout.LayoutParams(-1, -1));
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v,insets) -> {
                androidx.core.graphics.Insets safe = insets.getInsetsIgnoringVisibility(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        } else root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingBounds = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER);
        root.addView(loading, loadingBounds);

        error = new TextView(this);
        error.setTextColor(0xFFF5F5EF);
        error.setTextSize(16);
        error.setGravity(Gravity.CENTER);
        error.setPadding(dp(28), dp(24), dp(28), dp(24));
        error.setBackgroundColor(0xE6101113);
        error.setVisibility(View.GONE);
        FrameLayout.LayoutParams errorBounds = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        errorBounds.setMargins(dp(24), 0, dp(24), 0);
        root.addView(error, errorBounds);

        Button back = new Button(this);
        back.setText("‹  Back");
        back.setContentDescription("Close video preview");
        back.setAllCaps(false);
        back.setTextColor(0xFFF5F5EF);
        back.setTextSize(16);
        back.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        back.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xE61D1F22); background.setCornerRadius(dp(24));
        back.setBackground(background);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backBounds = new FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP | Gravity.START);
        backBounds.setMargins(dp(20), dp(36), dp(20), 0);
        root.addView(back, backBounds);
        setContentView(root);
        if (!isLocal(source)) showError("Choose a video saved on your phone to preview.");
        else if (!validRange) showError("Use a valid start and end inside the source clip to preview this cut.");
    }

    @Override protected void onStart() {
        super.onStart();
        if (!isLocal(source) || !validRange || player != null) return;
        try {
            MediaItem item = previewItem(this, source, startMs, endMs);
            error.setVisibility(View.GONE); loading.setVisibility(View.VISIBLE);
            player = new ExoPlayer.Builder(this).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true);
            player.setPlayWhenReady(false);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int playbackState) {
                    loading.setVisibility(playbackState == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                    if (playbackState == Player.STATE_READY) playerView.showController();
                }
                @Override public void onIsPlayingChanged(boolean playing) {
                    if (playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                @Override public void onPlayerError(PlaybackException failure) {
                    showError("This clip could not be opened. It may be unavailable or use an unsupported format.");
                }
            });
            playerView.setPlayer(player);
            player.setMediaItem(item);
            // Media3's clipped timeline starts at zero; extras retain source timestamps.
            player.seekTo(startMs == null ? positionMs : Math.min(positionMs, endMs - startMs - 1));
            player.prepare();
            playerView.showController();
        } catch (Exception failure) {
            releasePlayer();
            showError("This clip could not be opened. Go back and choose another video.");
        }
    }

    @Override protected void onStop() {
        releasePlayer();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong(POSITION, player == null ? positionMs : Math.max(0, player.getCurrentPosition()));
        if (source != null) state.putString(SOURCE, source.toString());
        if (startMs != null) state.putLong(EXTRA_START_MS, startMs);
        if (endMs != null) state.putLong(EXTRA_END_MS, endMs);
        state.putBoolean(EXTRA_CROP_TO_REEL, cropToReel);
        super.onSaveInstanceState(state);
    }

    private void releasePlayer() {
        if (player != null) {
            positionMs = Math.max(0, player.getCurrentPosition());
            player.pause(); playerView.setPlayer(null); player.release(); player = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (loading != null) loading.setVisibility(View.GONE);
    }

    private void showError(String message) {
        if (player != null) player.pause();
        loading.setVisibility(View.GONE);
        error.setText(message); error.setVisibility(View.VISIBLE);
    }

    private static boolean isLocal(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        if ("content".equalsIgnoreCase(uri.getScheme())) return uri.getAuthority() != null && !uri.getAuthority().isEmpty();
        if ("file".equalsIgnoreCase(uri.getScheme())) return uri.getAuthority() == null || uri.getAuthority().isEmpty();
        return false;
    }

    /** Headless configuration helper. Only a selected range needs metadata inspection. */
    static MediaItem previewItem(android.content.Context context, Uri uri, Long start, Long end) throws java.io.IOException {
        if (!isLocal(uri)) throw new IllegalArgumentException("Choose a local clip.");
        validateRange(start, end, -1);
        if (start == null) return MediaItem.fromUri(uri);
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, uri);
            if (!"yes".equalsIgnoreCase(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))
                throw new IllegalArgumentException("Choose a video clip.");
            long duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (duration <= 0) throw new IllegalArgumentException("Source duration unavailable.");
            validateRange(start, end, duration);
        } finally { metadata.release(); }
        return new MediaItem.Builder().setUri(uri)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start).setEndPositionMs(end).build()).build();
    }

    static void validateRange(Long start, Long end, long sourceDuration) {
        if (start == null && end == null) return;
        if (start == null || end == null || start < 0 || end <= start
                || sourceDuration >= 0 && end > sourceDuration)
            throw new IllegalArgumentException("Use a valid cut range inside the source clip.");
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
