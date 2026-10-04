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
import androidx.media3.effect.Brightness;
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
    private ExportRecovery.Journal activeJournal;
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
        export(takes, title, look, Collections.emptyList(), listener);
    }

    public void export(List<Take> takes, String title, String look,
            List<AutoColorBalance.Balance> balances, Listener listener) {
        export(takes, title, look, balances, ShotPlanSnapshot.empty(), listener);
    }

    /** shotPlan is an immutable creator-visible metadata snapshot captured at dispatch. */
    public void export(List<Take> takes, String title, String look,
            List<AutoColorBalance.Balance> balances, ShotPlanSnapshot shotPlan, Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> export(takes, title, look, balances, shotPlan, listener));
            return;
        }
        if (busy) { listener.onError("An export is already running. Cancel it before starting another."); return; }
        List<Take> cuts = new ArrayList<>();
        try {
            if (shotPlan == null) throw new IllegalArgumentException("The current shot plan is missing. Review it before exporting.");
            final List<AutoColorBalance.Balance> measured = balances == null
                    ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(balances));
            boolean heading = title != null && !title.trim().isEmpty();
            if (heading) headingSize(title.trim(), 120, 25, 14, "Reel title");
            long total = 0;
            for (Take take : takes) {
                if (!take.selected) continue;
                if (take.uri == null || take.durationMs <= 0 || take.inMs < 0
                        || take.outMs > take.durationMs || take.outMs - take.inMs < 100) {
                    throw new IllegalArgumentException("Check the in/out points for " + take.title + ".");
                }
                Take copy = new Take(take.uri, take.shotId, take.title, take.caption, take.durationMs);
                copy.inMs = take.inMs; copy.outMs = take.outMs;
                copy.reviewedShotIds = reviewedShotIdsSnapshot(take);
                if (heading) headingSize(copy.title, 140, 19, 12, "Take title");
                checkCaptionLength(copy.caption);
                copy.captionOrigin = take.captionOrigin == null ? "manual" : take.captionOrigin;
                if (take.subtitles != null && take.subtitles.size() > 500)
                    throw new IllegalArgumentException("Too many subtitle segments in " + take.title + ".");
                if (take.subtitles != null) for (SubtitleCue cue : take.subtitles) {
                    if (cue == null || cue.text == null || cue.text.trim().isEmpty()) continue;
                    long start = Math.max(0, cue.startMs), end = Math.min(take.durationMs, cue.endMs);
                    checkCaptionLength(cue.text);
                    if (end > start) {
                        if (end > copy.inMs && start < copy.outMs) captionLayout(cue.text.trim());
                        copy.subtitles.add(new SubtitleCue(start, end, cue.text));
                    }
                }
                SubtitleTimeline.requireNonOverlapping(copy.subtitles);
                if (copy.subtitles.size() > 500) throw new IllegalArgumentException("Too many subtitle segments in " + take.title + ".");
                if (copy.subtitles.isEmpty()) captionLayout(copy.caption == null ? "" : copy.caption.trim());
                if ("Auto balance".equalsIgnoreCase(look)) matchingBalance(copy, measured);
                cuts.add(copy); total += copy.outMs - copy.inMs;
            }
            if (cuts.isEmpty()) throw new IllegalArgumentException("Choose at least one take to export.");
            if (cuts.size() > 12 || total > 180_000) {
                throw new IllegalArgumentException("This research exporter supports up to 12 takes and 3 minutes.");
            }
            busy = true;
            final int run = ++generation;
            activeListener = listener;
            activeJournal = ExportRecovery.begin(context);
            activeFile = activeJournal.temp();
            if (!activeFile.createNewFile()) throw new IllegalStateException("Export temporary file already exists.");
            final File output = activeFile;
            final ExportRecovery.Journal journal = activeJournal;
            List<EditedMediaItem> items = new ArrayList<>();
            long timelineStartMs = 0;
            for (Take cut : cuts) {
                MediaItem media = new MediaItem.Builder().setUri(cut.uri)
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(cut.inMs).setEndPositionMs(cut.outMs).build()).build();
                List<Effect> effects = new ArrayList<>();
                effects.add(Presentation.createForWidthAndHeight(720, 1280,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
                if ("Auto balance".equalsIgnoreCase(look)) {
                    AutoColorBalance.Balance balance = matchingBalance(cut, measured);
                    if (balance.applied) {
                        effects.add(new RgbAdjustment.Builder().setRedScale(balance.redScale)
                                .setGreenScale(balance.greenScale).setBlueScale(balance.blueScale).build());
                        if (balance.brightness != 0) effects.add(new Brightness(balance.brightness));
                    }
                } else if ("Warm".equalsIgnoreCase(look)) {
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
                            new Thread(() -> publish(run, output, journal, cuts, title, look, measured, shotPlan, listener), "Reel-save").start();
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
        if (activeJournal != null) activeJournal.abort();
        activeJournal = null;
        if (activeFile != null) activeFile.delete();
        activeFile = null; busy = false;
        Listener listener = activeListener; activeListener = null;
        if (listener != null) listener.onError("Export cancelled. Originals are safe.");
    }

    private void fail(String message) {
        main.removeCallbacks(progress);
        if (transformer != null) { transformer.cancel(); transformer = null; }
        if (activeJournal != null) activeJournal.abort();
        activeJournal = null;
        if (activeFile != null) activeFile.delete();
        activeFile = null; busy = false;
        Listener listener = activeListener; activeListener = null;
        if (listener != null) listener.onError(message);
    }

    private void publish(int run, File output, ExportRecovery.Journal journal, List<Take> cuts,
            String title, String look, List<AutoColorBalance.Balance> measured,
            ShotPlanSnapshot shotPlan, Listener listener) {
        Uri video = null;
        File edit = null;
        try {
            if (run != generation) return;
            if (output.length() < 1000) throw new IllegalStateException("No playable video was produced.");
            journal.saving();
            File directory = new File(context.getFilesDir(), "exports");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Edit folder unavailable.");
            edit = journal.edit();
            JSONObject project = editDocument(journal.id, cuts, title, look, measured, shotPlan);
            try (FileOutputStream stream = new FileOutputStream(edit)) {
                stream.write(project.toString(2).getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            Uri editUri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", edit);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, journal.videoName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MiniFilm");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            video = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (video == null) throw new IllegalStateException("Gallery storage unavailable.");
            journal.rowInserted(video);
            try (FileInputStream input = new FileInputStream(output);
                    OutputStream destination = context.getContentResolver().openOutputStream(video)) {
                if (destination == null) throw new IllegalStateException("Cannot write gallery video.");
                byte[] buffer = new byte[64 * 1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (run != generation) throw new IllegalStateException("Export cancelled.");
                    destination.write(buffer, 0, count);
                }
                destination.flush();
                if (destination instanceof FileOutputStream) ((FileOutputStream) destination).getFD().sync();
            }
            // Durable READY precedes IS_PENDING=0, allowing commit-gap recovery.
            journal.ready(output.length());
            final Uri savedVideo = video;
            final File savedEdit = edit;
            main.post(() -> {
                if (run != generation) {
                    cleanupInterrupted(journal, savedVideo, savedEdit, output); return;
                }
                try {
                    ContentValues done = new ContentValues(); done.put(MediaStore.Video.Media.IS_PENDING, 0);
                    if (context.getContentResolver().update(savedVideo, done, null, null) != 1)
                        throw new IllegalStateException("Could not publish gallery video.");
                } catch (Exception error) {
                    // A provider failure can leave publication outcome unknown. Keep READY:
                    // startup will preserve a verified published pair or remove pending work.
                    journal.settled();
                    activeFile = null; activeJournal = null; activeListener = null; busy = false;
                    listener.onError("Could not confirm publication. Reopen the app to check this export. Originals are safe.");
                    return;
                }
                // Once published, preserve successful outputs even if journal/UI callbacks fail.
                try { journal.complete(); }
                catch (Exception error) { Log.w("MiniFilmExport", "Published export retained with READY recovery journal", error); }
                journal.settled();
                activeFile = null; activeJournal = null; activeListener = null; busy = false;
                try {
                    listener.onProgress(100); listener.onComplete(savedVideo, editUri);
                    Log.i("MiniFilmExport", "EXPORT_OK durationMs=" + project.optLong("durationMs")
                            + " size=" + output.length() + " width=720 height=1280 cuts=" + cuts.size());
                } catch (Exception error) { Log.w("MiniFilmExport", "Published export retained after UI callback failure", error); }
                finally { output.delete(); }
            });
        } catch (Exception e) {
            cleanupInterrupted(journal, video, edit, output);
            main.post(() -> { if (run == generation) fail("Could not save export: " + safeMessage(e)); });
        }
    }

    /** Pure edit-document serializer; inputs are the export's validated, isolated snapshots. */
    static JSONObject editDocument(String exportId, List<Take> cuts, String title, String look,
            List<AutoColorBalance.Balance> measured) throws Exception {
        return editDocument(exportId, cuts, title, look, measured, ShotPlanSnapshot.empty());
    }

    static JSONObject editDocument(String exportId, List<Take> cuts, String title, String look,
            List<AutoColorBalance.Balance> measured, ShotPlanSnapshot shotPlan) throws Exception {
        if (shotPlan == null) throw new IllegalArgumentException("The current shot plan is missing. Review it before exporting.");
        JSONObject project = new JSONObject();
        project.put("schema", "minifilm.edit.v1"); project.put("preEventResearch", true);
        project.put("exportId", exportId);
        project.put("title", title); project.put("look", look);
        project.put("width", 720); project.put("height", 1280);
        project.put("crop", "center_9_16_review_framing");
        project.put("captions", "manual captions or editable offline English ASR drafts; review text and timing");
        project.put("audio", "source audio per sequential cut; silent takes padded; no multi-camera sync");
        project.put("shotMappingPolicy", "Explicit creator assignments only; stored IDs do not establish quality or a current plan match.");
        project.put("shotPlan", shotPlan.toJson());
        JSONArray list = new JSONArray(); long timeline = 0;
        for (Take cut : cuts) {
            JSONObject entry = new JSONObject();
            entry.put("sourceUri", cut.uri.toString()); entry.put("shotId", cut.shotId);
            putReviewedShotMapping(entry, cut);
            entry.put("title", cut.title); entry.put("caption", cut.caption);
            entry.put("captionOrigin", cut.captionOrigin);
            entry.put("inMs", cut.inMs); entry.put("outMs", cut.outMs);
            entry.put("timelineStartMs", timeline);
            if ("Auto balance".equalsIgnoreCase(look))
                entry.put("colorBalance", balanceJson(matchingBalance(cut, measured)));
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
        project.put("durationMs", timeline); project.put("cuts", list); return project;
    }

    /** String IDs are immutable; every cut owns a separate mutable list snapshot. */
    static List<String> reviewedShotIdsSnapshot(Take take) {
        return take.reviewedShotIds == null ? new ArrayList<>() : new ArrayList<>(take.reviewedShotIds);
    }

    static void putReviewedShotMapping(JSONObject entry, Take cut) throws Exception {
        List<String> ids = cut.reviewedShotIds == null ? Collections.emptyList() : cut.reviewedShotIds;
        entry.put("reviewedShotIds", new JSONArray(ids));
        entry.put("reviewedShotMappingOrigin", ids.isEmpty() ? "unassigned" : "creator-reviewed");
    }

    private void cleanupInterrupted(ExportRecovery.Journal journal, Uri video, File edit, File output) {
        journal.abort(); // Persist cleanup scope before best-effort provider operations.
        try {
            if (video != null) context.getContentResolver().delete(video, null, null);
        } catch (Exception error) { Log.w("MiniFilmExport", "Pending export retained for startup cleanup", error); }
        if (edit != null) edit.delete();
        output.delete(); journal.settled();
    }

    private static AutoColorBalance.Balance matchingBalance(Take cut, List<AutoColorBalance.Balance> measured) {
        for (AutoColorBalance.Balance balance : measured) {
            if (balance == null || !balance.matches(cut)) continue;
            for (float gain : new float[] { balance.redScale, balance.greenScale, balance.blueScale })
                if (!Float.isFinite(gain) || gain < .94f || gain > 1.06f)
                    throw new IllegalArgumentException("Auto balance parameters are invalid. Analyze this selection again.");
            if (!Float.isFinite(balance.brightness) || Math.abs(balance.brightness) > .04f)
                throw new IllegalArgumentException("Auto balance exposure is invalid. Analyze this selection again.");
            return balance;
        }
        throw new IllegalArgumentException("Auto balance no longer matches this source, shot and trim. Analyze this selection again or choose Clean.");
    }

    private static JSONObject balanceJson(AutoColorBalance.Balance balance) throws Exception {
        JSONArray sampled = new JSONArray(); for (long at : balance.sampledAtMs) sampled.put(at);
        return new JSONObject().put("method", "deterministic-neutral-exposure-heuristic-v1")
                .put("backend", "local-cpu-frame-statistics").put("learned", false).put("reviewRequired", true)
                .put("sourceUri", balance.uri.toString()).put("shotId", balance.shotId)
                .put("inMs", balance.inMs).put("outMs", balance.outMs)
                .put("applied", balance.applied).put("reason", balance.reason)
                .put("redScale", balance.redScale).put("greenScale", balance.greenScale)
                .put("blueScale", balance.blueScale).put("brightnessLinearBt709", balance.brightness)
                .put("neutralRed", balance.neutralRed).put("neutralGreen", balance.neutralGreen)
                .put("neutralBlue", balance.neutralBlue).put("meanLuma", balance.meanLuma)
                .put("lumaStdDev", balance.lumaStdDev).put("neutralFraction", balance.neutralFraction)
                .put("clippedFraction", balance.clippedFraction).put("sampledFrames", balance.sampledFrames)
                .put("sampledAtSourceMs", sampled);
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
            p.setTextSize(headingSize(title.trim(), 120, 25, 14, "Reel title"));
            p.setShadowLayer(4, 0, 2, Color.BLACK);
            canvas.drawText(title.trim(), 42, 112, p);
            p.setTextSize(headingSize(shotTitle, 140, 19, 12, "Take title"));
            canvas.drawText(shotTitle == null ? "" : shotTitle, 42, 143, p);
        }
        String text = caption == null ? "" : caption.trim();
        if (!text.isEmpty()) {
            StaticLayout layout = captionLayout(text);
            int top = 1120 - layout.getHeight();
            p.clearShadowLayer(); p.setColor(0xCC171A21);
            canvas.drawRoundRect(34, top - 24, 686, 1144, 24, 24, p);
            canvas.save(); canvas.translate(56, top); layout.draw(canvas); canvas.restore();
        }
        return bitmap;
    }

    private static void checkCaptionLength(String text) {
        if (text != null && text.length() > 1000)
            throw new IllegalArgumentException("Caption exceeds 1,000 characters. Shorten the text or edit subtitle segments before exporting.");
    }

    /** Full text only: never cap layout lines, elide words or invent subtitle timing. */
    static StaticLayout captionLayout(String text) {
        if (text == null) text = "";
        checkCaptionLength(text);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        for (int size = 35; size >= 24; size--) {
            paint.setTextSize(size);
            StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 608)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(5, 1).build();
            if (layout.getLineCount() <= 4
                    && layout.getLineEnd(layout.getLineCount() - 1) == text.length()) return layout;
        }
        throw new IllegalArgumentException("Caption does not fit four lines. Shorten the words or edit subtitle segments and timing before exporting.");
    }

    private static float headingSize(String text, int maxChars, int initial, int minimum, String name) {
        if (text == null) text = "";
        if (text.length() > maxChars)
            throw new IllegalArgumentException(name + " exceeds " + maxChars + " characters. Shorten it before exporting.");
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
            throw new IllegalArgumentException(name + " must fit one line. Remove line breaks before exporting.");
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        for (int size = initial; size >= minimum; size--) {
            paint.setTextSize(size);
            if (paint.measureText(text) <= 636) return size;
        }
        throw new IllegalArgumentException(name + " is too wide. Shorten it before exporting.");
    }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
