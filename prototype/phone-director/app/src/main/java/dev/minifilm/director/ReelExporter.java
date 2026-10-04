package dev.minifilm.director;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import androidx.core.content.FileProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbAdjustment;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import com.google.common.collect.ImmutableList;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pre-event research: on-phone editing, deterministic looks and manual captions. */
public final class ReelExporter {
    public interface Listener {
        void onProgress(int percent);
        void onComplete(Uri videoUri, Uri editListUri);
        void onError(String message);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int generation;
    private Transformer transformer;
    private File activeFile;
    private Listener activeListener;
    private boolean busy;
    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (transformer == null) return;
            ProgressHolder value = new ProgressHolder();
            if (transformer.getProgress(value) == Transformer.PROGRESS_STATE_AVAILABLE
                    && activeListener != null) activeListener.onProgress(Math.min(94, value.progress));
            main.postDelayed(this, 350);
        }
    };

    public ReelExporter(Context context) { this.context = context.getApplicationContext(); }

    public void export(List<Take> takes, String title, String look, Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> export(takes, title, look, listener));
            return;
        }
        if (busy) { listener.onError("An export is already running. Cancel it before starting another."); return; }
        List<Take> cuts = new ArrayList<>();
        try {
            long total = 0;
            for (Take take : takes) {
                if (!take.selected) continue;
                if (take.uri == null || take.durationMs <= 0 || take.inMs < 0
                        || take.outMs > take.durationMs || take.outMs - take.inMs < 100) {
                    throw new IllegalArgumentException("Check the in/out points for " + take.title + ".");
                }
                Take copy = new Take(take.uri, take.shotId, take.title, take.caption, take.durationMs);
                copy.inMs = take.inMs; copy.outMs = take.outMs;
                copy.captionOrigin = take.captionOrigin == null ? "manual" : take.captionOrigin;
                if (take.subtitles != null) for (SubtitleCue cue : take.subtitles) {
                    if (cue == null || cue.text == null || cue.text.trim().isEmpty()) continue;
                    long start = Math.max(0, cue.startMs), end = Math.min(take.durationMs, cue.endMs);
                    if (end > start) copy.subtitles.add(new SubtitleCue(start, end, cue.text));
                }
                if (copy.subtitles.size() > 500) throw new IllegalArgumentException("Too many subtitle segments in " + take.title + ".");
                cuts.add(copy); total += copy.outMs - copy.inMs;
            }
            if (cuts.isEmpty()) throw new IllegalArgumentException("Choose at least one take to export.");
            if (cuts.size() > 12 || total > 180_000) {
                throw new IllegalArgumentException("This research exporter supports up to 12 takes and 3 minutes.");
            }
            busy = true;
            final int run = ++generation;
            activeListener = listener;
            activeFile = File.createTempFile("reel-", ".mp4", context.getCacheDir());
            final File output = activeFile;
            List<EditedMediaItem> items = new ArrayList<>();
            long timelineStartMs = 0;
            for (Take cut : cuts) {
                MediaItem media = new MediaItem.Builder().setUri(cut.uri)
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(cut.inMs).setEndPositionMs(cut.outMs).build()).build();
                List<Effect> effects = new ArrayList<>();
                effects.add(Presentation.createForWidthAndHeight(720, 1280,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
                if ("Warm".equalsIgnoreCase(look)) {
                    effects.add(new RgbAdjustment.Builder().setRedScale(1.04f).setBlueScale(.96f).build());
                } else if ("Cinematic".equalsIgnoreCase(look)) {
                    effects.add(new Contrast(.08f));
                    effects.add(new RgbAdjustment.Builder().setRedScale(1.01f).setBlueScale(1.02f).build());
                }
                effects.add(new OverlayEffect(ImmutableList.of(
                        new CaptionOverlay(title, cut, timelineStartMs))));
                items.add(new EditedMediaItem.Builder(media)
                        .setEffects(new Effects(Collections.emptyList(), effects)).build());
                timelineStartMs += cut.outMs - cut.inMs;
            }
            Composition composition = new Composition.Builder(new EditedMediaItemSequence.Builder(items).build())
                    .experimentalSetForceAudioTrack(true)
                    .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build();
            transformer = new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC).setPortraitEncodingEnabled(true)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            if (run != generation) return;
                            main.removeCallbacks(progress); transformer = null;
                            listener.onProgress(95);
                            new Thread(() -> publish(run, output, cuts, title, look, listener), "Reel-save").start();
                        }
                        @Override public void onError(Composition composition, ExportResult result,
                                ExportException exception) {
                            if (run != generation) return;
                            Log.e("MiniFilmExport", "Export failed: " + exception.getErrorCodeName(), exception);
                            fail("Export failed (" + exception.getErrorCodeName() + "). Originals are safe.");
                        }
                    }).build();
            listener.onProgress(0);
            transformer.start(composition, output.getAbsolutePath());
            main.post(progress);
        } catch (Exception e) {
            if (busy) fail("Could not start export: " + safeMessage(e));
            else listener.onError(safeMessage(e));
        }
    }

    public void cancel() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::cancel); return; }
        ++generation;
        main.removeCallbacks(progress);
        if (transformer != null) { transformer.cancel(); transformer = null; }
        if (activeFile != null) activeFile.delete();
        activeFile = null; busy = false;
        Listener listener = activeListener; activeListener = null;
        if (listener != null) listener.onError("Export cancelled. Originals are safe.");
    }

    private void fail(String message) {
        main.removeCallbacks(progress);
        if (transformer != null) { transformer.cancel(); transformer = null; }
        if (activeFile != null) activeFile.delete();
        activeFile = null; busy = false;
        Listener listener = activeListener; activeListener = null;
        if (listener != null) listener.onError(message);
    }

    private void publish(int run, File output, List<Take> cuts, String title, String look, Listener listener) {
        Uri video = null;
        File edit = null;
        try {
            if (run != generation) return;
            if (output.length() < 1000) throw new IllegalStateException("No playable video was produced.");
            String stem = "MiniFilm-" + System.currentTimeMillis();
            File directory = new File(context.getFilesDir(), "exports");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Edit folder unavailable.");
            edit = new File(directory, stem + ".json");
            JSONObject project = new JSONObject();
            project.put("schema", "minifilm.edit.v1"); project.put("preEventResearch", true);
            project.put("title", title); project.put("look", look);
            project.put("width", 720); project.put("height", 1280);
            project.put("crop", "center_9_16_review_framing");
            project.put("captions", "manual captions or editable offline English ASR drafts; review text and timing");
            project.put("audio", "source audio per sequential cut; silent takes padded; no multi-camera sync");
            JSONArray list = new JSONArray(); long timeline = 0;
            for (Take cut : cuts) {
                JSONObject entry = new JSONObject();
                entry.put("sourceUri", cut.uri.toString()); entry.put("shotId", cut.shotId);
                entry.put("title", cut.title); entry.put("caption", cut.caption);
                entry.put("captionOrigin", cut.captionOrigin);
                entry.put("inMs", cut.inMs); entry.put("outMs", cut.outMs);
                entry.put("timelineStartMs", timeline);
                JSONArray subtitles = new JSONArray();
                for (SubtitleCue cue : cut.subtitles) {
                    JSONObject subtitle = new JSONObject();
                    subtitle.put("sourceStartMs", cue.startMs); subtitle.put("sourceEndMs", cue.endMs);
                    subtitle.put("text", cue.text);
                    long start = Math.max(cut.inMs, cue.startMs), end = Math.min(cut.outMs, cue.endMs);
                    subtitle.put("visibleInCut", end > start);
                    if (end > start) {
                        subtitle.put("timelineStartMs", timeline + start - cut.inMs);
                        subtitle.put("timelineEndMs", timeline + end - cut.inMs);
                    }
                    subtitles.put(subtitle);
                }
                entry.put("subtitles", subtitles);
                timeline += cut.outMs - cut.inMs;
                list.put(entry);
            }
            project.put("durationMs", timeline); project.put("cuts", list);
            try (FileOutputStream stream = new FileOutputStream(edit)) {
                stream.write(project.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Uri editUri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", edit);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, stem + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MiniFilm");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            video = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (video == null) throw new IllegalStateException("Gallery storage unavailable.");
            try (FileInputStream input = new FileInputStream(output);
                    OutputStream destination = context.getContentResolver().openOutputStream(video)) {
                if (destination == null) throw new IllegalStateException("Cannot write gallery video.");
                byte[] buffer = new byte[64 * 1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (run != generation) throw new IllegalStateException("Export cancelled.");
                    destination.write(buffer, 0, count);
                }
            }
            final Uri savedVideo = video;
            final File savedEdit = edit;
            main.post(() -> {
                if (run != generation) {
                    context.getContentResolver().delete(savedVideo, null, null); savedEdit.delete(); return;
                }
                try {
                    ContentValues done = new ContentValues(); done.put(MediaStore.Video.Media.IS_PENDING, 0);
                    if (context.getContentResolver().update(savedVideo, done, null, null) != 1)
                        throw new IllegalStateException("Could not publish gallery video.");
                    activeFile = null; activeListener = null; busy = false;
                    listener.onProgress(100); listener.onComplete(savedVideo, editUri);
                    Log.i("MiniFilmExport", "EXPORT_OK durationMs=" + project.optLong("durationMs")
                            + " size=" + output.length() + " width=720 height=1280 cuts=" + cuts.size());
                } catch (Exception error) {
                    context.getContentResolver().delete(savedVideo, null, null); savedEdit.delete();
                    fail("Could not save export: " + safeMessage(error));
                } finally { output.delete(); }
            });
        } catch (Exception e) {
            if (video != null) context.getContentResolver().delete(video, null, null);
            if (edit != null) edit.delete();
            output.delete();
            main.post(() -> { if (run == generation) fail("Could not save export: " + safeMessage(e)); });
        }
    }

    /** Media3 1.5.1 effects see composition timestamps, after clipping and item offset. */
    private static final class CaptionOverlay extends BitmapOverlay {
        private final String title;
        private final Take cut;
        private final long timelineStartMs;
        private int currentCue = Integer.MIN_VALUE;
        private Bitmap bitmap;
        CaptionOverlay(String title, Take cut, long timelineStartMs) {
            this.title = title; this.cut = cut; this.timelineStartMs = timelineStartMs;
        }
        @Override public Bitmap getBitmap(long presentationTimeUs) {
            long sourceMs = presentationTimeUs / 1000 - timelineStartMs + cut.inMs;
            int index = -1;
            for (int i = 0; i < cut.subtitles.size(); i++) {
                SubtitleCue cue = cut.subtitles.get(i);
                if (sourceMs >= cue.startMs && sourceMs < cue.endMs) { index = i; break; }
            }
            if (bitmap == null || index != currentCue) {
                String caption = cut.subtitles.isEmpty() ? cut.caption : index < 0 ? "" : cut.subtitles.get(index).text;
                bitmap = typography(title, cut.title, caption);
                currentCue = index;
            }
            return bitmap;
        }
    }

    private static Bitmap typography(String title, String shotTitle, String caption) {
        Bitmap bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (title != null && !title.trim().isEmpty()) {
            p.setColor(0xFF171A21); canvas.drawRoundRect(28, 40, 692, 164, 20, 20, p);
            p.setColor(Color.rgb(207, 255, 108)); canvas.drawRoundRect(42, 65, 192, 73, 4, 4, p);
            p.setColor(Color.WHITE); p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(25); p.setShadowLayer(4, 0, 2, Color.BLACK);
            canvas.drawText(limit(title.trim(), 42), 42, 112, p);
            p.setTextSize(19); canvas.drawText(limit(shotTitle, 52), 42, 143, p);
        }
        String text = caption == null ? "" : caption.trim();
        if (!text.isEmpty()) {
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE); textPaint.setTextSize(35);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            StaticLayout layout = StaticLayout.Builder.obtain(limit(text, 180), 0,
                    limit(text, 180).length(), textPaint, 608).setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(4).setLineSpacing(5, 1).build();
            int top = 1120 - layout.getHeight();
            p.clearShadowLayer(); p.setColor(0xCC171A21);
            canvas.drawRoundRect(34, top - 24, 686, 1144, 24, 24, p);
            canvas.save(); canvas.translate(56, top); layout.draw(canvas); canvas.restore();
        }
        return bitmap;
    }

    private static String limit(String value, int length) {
        if (value == null) return "";
        return value.length() > length ? value.substring(0, length - 1) + "…" : value;
    }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
