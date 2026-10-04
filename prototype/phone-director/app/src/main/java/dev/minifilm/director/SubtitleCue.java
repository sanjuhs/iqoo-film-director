package dev.minifilm.director;

/** Editable draft speech text with source-video timestamps in milliseconds. */
public final class SubtitleCue {
    public long startMs, endMs;
    public String text;
    public SubtitleCue(long startMs, long endMs, String text) {
        this.startMs = startMs; this.endMs = endMs; this.text = text;
    }
}
