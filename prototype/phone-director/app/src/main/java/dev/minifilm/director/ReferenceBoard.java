package dev.minifilm.director;

import android.graphics.Bitmap;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Sparse local frame drafts. Only explicit creator review produces planning text. */
public final class ReferenceBoard implements AutoCloseable {
    public static final int MAX_NOTES = 300;
    public static final int MAX_SUMMARY = 210;
    public final Uri sourceUri;
    public final List<FrameDraft> frames;
    public final boolean reviewed;

    /** Text is immutable; the privately owned thumbnail can be disposed independently. */
    public static final class FrameDraft implements AutoCloseable {
        public final long requestedTimeMs;
        public final String originalNotes;
        public final String notes;
        public final String modelLabel;
        public final boolean selected;
        private Bitmap thumbnail;

        /** Copies the supplied bitmap. Caller retains ownership of the supplied bitmap and this draft. */
        public FrameDraft(long requestedTimeMs, String originalNotes, String modelLabel, Bitmap thumbnail) {
            this(requestedTimeMs, originalNotes, originalNotes, modelLabel, true, thumbnail);
        }
        private FrameDraft(long time, String original, String notes, String label, boolean selected, Bitmap image) {
            validateTime(time);
            this.requestedTimeMs = time;
            this.originalNotes = text(original, MAX_NOTES, "Frame draft");
            this.notes = text(notes, MAX_NOTES, "Frame notes");
            this.modelLabel = text(label, 160, "Frame provenance");
            this.selected = selected;
            this.thumbnail = copyThumbnail(image);
        }
        /** Independent caller-owned copy; recycle it only after removing it from displayed views. */
        public synchronized Bitmap thumbnailCopy() {
            return thumbnail == null ? null : thumbnail.copy(Bitmap.Config.ARGB_8888, false);
        }
        private synchronized FrameDraft copyWith(String corrected, boolean chosen) {
            return new FrameDraft(requestedTimeMs, originalNotes, corrected, modelLabel, chosen, thumbnail);
        }
        @Override public synchronized void close() {
            if (thumbnail != null) { thumbnail.recycle(); thumbnail = null; }
        }
    }

    /** Copies all drafts; caller may close its original drafts immediately afterward. */
    public ReferenceBoard(Uri sourceUri, List<FrameDraft> frames) { this(sourceUri, frames, false); }
    private ReferenceBoard(Uri uri, List<FrameDraft> drafts, boolean reviewed) {
        validateUri(uri);
        if (drafts == null || drafts.isEmpty() || drafts.size() > 3)
            throw new IllegalArgumentException("Choose one to three reference moments.");
        ArrayList<FrameDraft> copies = new ArrayList<>();
        try {
            long previous = -1;
            for (FrameDraft draft : drafts) {
                if (draft == null || draft.requestedTimeMs <= previous)
                    throw new IllegalArgumentException("Reference moments must be distinct and in time order.");
                copies.add(draft.copyWith(draft.notes, draft.selected));
                previous = draft.requestedTimeMs;
            }
            this.sourceUri = uri;
            this.frames = Collections.unmodifiableList(copies);
            this.reviewed = reviewed;
            if (reviewed) planningSummary();
        } catch (RuntimeException failure) {
            for (FrameDraft copy : copies) copy.close();
            throw failure;
        }
    }

    public ReferenceBoard reviewedCopy(List<String> correctedNotes) {
        return reviewedCopy(correctedNotes, Collections.nCopies(frames.size(), Boolean.TRUE));
    }
    /** Explicit review, including creator selection. Does not mutate or close the original board. */
    public ReferenceBoard reviewedCopy(List<String> correctedNotes, List<Boolean> selected) {
        if (correctedNotes == null || selected == null || correctedNotes.size() != frames.size()
                || selected.size() != frames.size())
            throw new IllegalArgumentException("Review every reference moment before using its notes.");
        ArrayList<FrameDraft> corrected = new ArrayList<>();
        try {
            for (int i = 0; i < frames.size(); i++) {
                if (selected.get(i) == null) throw new IllegalArgumentException("Choose which moments to use.");
                corrected.add(frames.get(i).copyWith(correctedNotes.get(i), selected.get(i)));
            }
            return new ReferenceBoard(sourceUri, corrected, true);
        } finally { for (FrameDraft draft : corrected) draft.close(); }
    }

    /** Compact whitespace and selected notes only; no summarizing model or silent text truncation. */
    public String planningSummary() {
        if (!reviewed) throw new IllegalStateException("Review and confirm the reference notes first.");
        StringBuilder result = new StringBuilder();
        for (FrameDraft frame : frames) if (frame.selected) {
            if (result.length() > 0) result.append("; ");
            result.append(timestamp(frame.requestedTimeMs)).append(' ')
                    .append(frame.notes.replaceAll("\\s+", " ").trim());
        }
        if (result.length() == 0) throw new IllegalArgumentException("Select at least one reference moment.");
        if (result.length() > MAX_SUMMARY)
            throw new IllegalArgumentException("Shorten the selected reference notes to " + MAX_SUMMARY
                    + " characters including times (currently " + result.length() + ").");
        return result.toString();
    }

    /** Confirmed text and a local URI only. No thumbnails or video bytes are serialized. */
    public String toJson() {
        planningSummary();
        try {
            JSONArray values = new JSONArray();
            for (FrameDraft frame : frames) values.put(new JSONObject()
                    .put("requestedTimeMs", frame.requestedTimeMs).put("notes", frame.notes)
                    .put("modelLabel", frame.modelLabel).put("selected", frame.selected));
            return new JSONObject().put("version", 1).put("sourceUri", sourceUri.toString())
                    .put("reviewed", true).put("frames", values).toString();
        } catch (Exception failure) { throw new IllegalStateException("Reference notes could not be saved.", failure); }
    }
    /** Restored text remains explicitly reviewed; thumbnail copies are null until reinspection. */
    public static ReferenceBoard fromJson(String json) {
        ArrayList<FrameDraft> drafts = new ArrayList<>();
        try {
            if (json == null || json.length() > 8000) throw new IllegalArgumentException();
            JSONObject object = new JSONObject(json);
            if (object.length() != 4 || integer(object.get("version")) != 1
                    || !Boolean.TRUE.equals(object.get("reviewed")) || !(object.get("sourceUri") instanceof String)
                    || !(object.get("frames") instanceof JSONArray)) throw new IllegalArgumentException();
            Uri uri = Uri.parse((String) object.get("sourceUri"));
            JSONArray array = object.getJSONArray("frames");
            if (array.length() < 1 || array.length() > 3) throw new IllegalArgumentException();
            for (int i = 0; i < array.length(); i++) {
                JSONObject frame = array.getJSONObject(i);
                if (frame.length() != 4 || !(frame.get("selected") instanceof Boolean)
                        || !(frame.get("notes") instanceof String)
                        || !(frame.get("modelLabel") instanceof String)) throw new IllegalArgumentException();
                drafts.add(new FrameDraft(integer(frame.get("requestedTimeMs")), frame.getString("notes"),
                        frame.getString("notes"), frame.getString("modelLabel"), frame.getBoolean("selected"), null));
            }
            return new ReferenceBoard(uri, drafts, true);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Saved reference notes are invalid. Review the reference again.", failure);
        } finally { for (FrameDraft draft : drafts) draft.close(); }
    }
    private static long integer(Object value) {
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException();
        return ((Number) value).longValue();
    }
    static void validateUri(Uri uri) {
        if (uri == null || uri.toString().length() > 2048
                || !("file".equals(uri.getScheme()) || "content".equals(uri.getScheme())))
            throw new IllegalArgumentException("Choose a reference video saved on your phone.");
        if ("file".equals(uri.getScheme()) && (uri.getPath() == null || !uri.getPath().startsWith("/")
                || (uri.getAuthority() != null && !uri.getAuthority().isEmpty()))
                || "content".equals(uri.getScheme()) && (uri.getAuthority() == null || uri.getAuthority().isEmpty()))
            throw new IllegalArgumentException("Choose a local reference video.");
    }
    static void validateTime(long time) {
        if (time < 0 || time > 179_999) throw new IllegalArgumentException("Choose a reference time below three minutes.");
    }
    private static String text(String value, int limit, String name) {
        if (value == null || value.trim().isEmpty() || value.length() > limit)
            throw new IllegalArgumentException(name + " must contain 1–" + limit + " characters.");
        for (int i = 0; i < value.length(); i++)
            if (Character.isISOControl(value.charAt(i)) && value.charAt(i) != '\n' && value.charAt(i) != '\t'
                    && value.charAt(i) != '\r') throw new IllegalArgumentException(name + " contains unsupported characters.");
        return value; // Preserve original draft exactly, including newlines.
    }
    private static Bitmap copyThumbnail(Bitmap image) {
        if (image == null) return null;
        if (image.isRecycled()) throw new IllegalArgumentException("Reference thumbnail has been released.");
        float factor = Math.min(1f, 256f / Math.max(image.getWidth(), image.getHeight()));
        Bitmap scaled = Bitmap.createScaledBitmap(image, Math.max(1, Math.round(image.getWidth() * factor)),
                Math.max(1, Math.round(image.getHeight() * factor)), true);
        try {
            Bitmap owned = scaled.copy(Bitmap.Config.ARGB_8888, false);
            if (owned == null) throw new IllegalStateException("Reference thumbnail could not be copied.");
            return owned;
        } finally { if (scaled != image) scaled.recycle(); }
    }
    private static String timestamp(long ms) {
        String base = String.format(Locale.ROOT, "%d:%02d", ms / 60_000, (ms / 1000) % 60);
        return ms % 1000 == 0 ? base : base + String.format(Locale.ROOT, ".%03d", ms % 1000);
    }
    @Override public void close() { for (FrameDraft frame : frames) frame.close(); }
}
