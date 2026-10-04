package dev.minifilm.director;

import android.net.Uri;

/** A creator-reviewed source and editable cut. Originals are never modified. */
public final class Take {
    public Uri uri;
    public String shotId, title, caption;
    public long durationMs, inMs, outMs;
    public boolean selected = true;
    /** Explicit creator-reviewed current-plan associations; capture shotId is only provenance. */
    public java.util.List<String> reviewedShotIds = new java.util.ArrayList<>();
    public java.util.List<SubtitleCue> subtitles = new java.util.ArrayList<>();
    public String captionOrigin = "manual";

    public Take(Uri uri, String shotId, String title, String caption, long durationMs) {
        this.uri = uri;
        this.shotId = shotId;
        this.title = title;
        this.caption = caption == null ? "" : caption;
        this.durationMs = durationMs;
        this.inMs = 0;
        this.outMs = durationMs;
    }
}
