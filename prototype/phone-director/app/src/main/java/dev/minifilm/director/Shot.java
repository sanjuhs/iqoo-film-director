package dev.minifilm.director;

/** Editable creator guidance. This schema contains no aircraft actions. */
public final class Shot {
    public String id;
    public String title;
    public String instruction;
    public String caption;
    public long targetDurationMs;

    public Shot(String id, String title, String instruction, String caption, long targetDurationMs) {
        this.id = id;
        this.title = title;
        this.instruction = instruction;
        this.caption = caption;
        this.targetDurationMs = Math.max(2000L, Math.min(60000L, targetDurationMs));
    }
}
