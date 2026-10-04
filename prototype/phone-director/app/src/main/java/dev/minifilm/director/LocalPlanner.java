package dev.minifilm.director;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fresh JNI integration of pinned llama.cpp. Text only; no image, network or flight API. */
public final class LocalPlanner {
    public interface Listener {
        void onPlan(List<Shot> shots, long elapsedMs, String modelLabel);
        void onError(String message);
    }
    public static final String MODEL_LABEL = "Qwen3.5 0.8B · local CPU · editable AI draft";
    private static boolean runtimeLoaded;
    static { try { System.loadLibrary("director_llm"); runtimeLoaded = true; } catch (LinkageError ignored) { } }
    private final File modelFile;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile long handle;
    private volatile boolean closed;

    public LocalPlanner(Context context) { modelFile = new File(context.getFilesDir(), "director-model.gguf"); }
    public boolean isModelAvailable() { return runtimeLoaded && modelFile.isFile() && modelFile.length() > 10000000; }

    public void generate(String brief, String style, Listener listener) {
        if (closed || !isModelAvailable()) { listener.onError("Local model isn't installed. Use the editable template."); return; }
        if (!busy.compareAndSet(false, true)) { listener.onError("The local planner is already working."); return; }
        String safeBrief = clip(brief, 500).replace("<|", "").replace("|>", "");
        String safeStyle = clip(style, 60).replace("<|", "").replace("|>", "");
        worker.execute(() -> {
            long began = SystemClock.elapsedRealtime();
            try {
                if (handle == 0) handle = nativeLoad(modelFile.getAbsolutePath());
                if (closed) return;
                String prompt = "<|im_start|>system\nYou direct a person making a phone reel. Return five ACTUAL RECORDED TAKES, never preparation, lighting setup, or background selection. " +
                        "Speak directly to the creator: pose, perform, talk, or show a detail. Adapt the shots to the brief. Keep the phone stationary and movements small. No aircraft actions. " +
                        sceneBeats(safeStyle) + " Each take lasts 3 to 8 seconds. " +
                        "Return ONLY a JSON array of exactly five objects. Each object has title (2 to 3 words), instruction (one concrete sentence, at most 12 words), caption (2 to 3 words), duration_ms (integer). " +
                        "No markdown, no explanation.\n<|im_end|>\n<|im_start|>user\nScene: " + safeStyle + "\nBrief: " + safeBrief +
                        "\n<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
                String result = new String(nativeGenerate(handle, prompt.getBytes(StandardCharsets.UTF_8), 580), StandardCharsets.UTF_8);
                List<Shot> plan = parse(result);
                long elapsed = SystemClock.elapsedRealtime() - began;
                Log.i("MiniFilmLocalAI", "planner_complete model=qwen35_0.8b backend=cpu shots=" + plan.size() + " elapsed_ms=" + elapsed);
                main.post(() -> { if (!closed) listener.onPlan(plan, elapsed, MODEL_LABEL); });
            } catch (Exception | LinkageError failure) {
                Log.w("MiniFilmLocalAI", "planner_failed type=" + failure.getClass().getSimpleName());
                main.post(() -> { if (!closed) listener.onError("Local AI couldn't produce a valid plan. Use the editable template or try a shorter brief."); });
            } finally { busy.set(false); }
        });
    }

    private static List<Shot> parse(String text) throws Exception {
        int start = text.indexOf('['), end = text.lastIndexOf(']');
        if (start < 0 || end < start) throw new IllegalArgumentException("No shot array");
        JSONArray array = new JSONArray(text.substring(start, end + 1));
        if (array.length() != 5) throw new IllegalArgumentException("Expected five shots");
        List<Shot> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.getJSONObject(i);
            String title = clip(row.getString("title"), 60);
            String instruction = clip(row.getString("instruction"), 220);
            String caption = clip(row.getString("caption"), 100);
            long duration = row.getLong("duration_ms");
            if (title.isEmpty() || instruction.isEmpty() || duration < 2000 || duration > 15000)
                throw new IllegalArgumentException("Invalid shot");
            result.add(new Shot("ai-shot-" + (i + 1), title, instruction, caption, duration));
        }
        return result;
    }
    private static String clip(String value, int length) {
        String clean = value == null ? "" : value.trim();
        return clean.length() > length ? clean.substring(0, length) : clean;
    }

    private static String sceneBeats(String style) {
        String scene = style.toLowerCase(Locale.ROOT);
        if (scene.contains("product")) return "The creator holds or uses the product. Shots in order: 1 person holding product hero; 2 slow reveal in their hands; 3 feature closeup; 4 demonstrate use; 5 person gives a verdict. No tabletop setup instructions.";
        if (scene.contains("intro")) return "Shots in order: 1 say your name; 2 explain what you do; 3 show a detail of your work; 4 share one fun personal fact; 5 welcome your audience.";
        if (scene.contains("talk") || scene.contains("story")) return "Shots in order: 1 speak the hook; 2 tell the context; 3 record a relevant cutaway; 4 explain the key idea; 5 speak the takeaway. Give specific spoken lines inspired by the brief.";
        return "This is a PERSON WEARING the outfit, never clothing on a flat surface. Shots in order: 1 full outfit hero pose; 2 two small steps after checking the path; 3 closeup of a worn garment detail; 4 side silhouette pose; 5 confident closing pose. Mention the brief's garment or color.";
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        // Native handle is only freed on the same worker after generation finishes.
        long current = handle;
        if (current != 0) nativeCancel(current);
        worker.execute(() -> { if (handle != 0) { nativeFree(handle); handle = 0; } });
        worker.shutdown();
    }
    private static native long nativeLoad(String path);
    private static native byte[] nativeGenerate(long handle, byte[] prompt, int maxTokens);
    private static native void nativeCancel(long handle);
    private static native void nativeFree(long handle);
}
