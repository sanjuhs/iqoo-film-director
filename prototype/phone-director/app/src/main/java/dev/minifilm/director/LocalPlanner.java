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
import java.util.regex.Pattern;

/** Fresh JNI integration of pinned llama.cpp. Text only; no image, network or flight API. */
public final class LocalPlanner {
    public interface Listener {
        void onPlan(List<Shot> shots, long elapsedMs, String modelLabel);
        void onError(String message);
    }
    public static final String MODEL_LABEL = "Qwen3.5 0.8B · local CPU · editable AI draft";
    private static final String[] FASHION_ROLES = {"Hero pose", "Movement", "Detail", "Side pose", "Closing"};
    private static final String[] PRODUCT_ROLES = {"Hero", "Reveal", "Detail", "In use", "Verdict"};
    private static final String[] TALKING_ROLES = {"Hook", "Context", "Cutaway", "Key idea", "Takeaway"};
    private static final String[] INTRO_ROLES = {"Name", "Work", "Detail", "Personal fact", "Welcome"};
    private static final String[] ACTION_VERBS = {"Stand", "Pose", "Take", "Turn", "Show", "Hold", "Look", "Walk",
            "Say", "Tell", "Explain", "Share", "Introduce", "Welcome", "Give", "Move", "Lift", "Face", "Point",
            "Bring", "Keep", "Smile", "Wave", "Tilt", "Pause"};
    private static final String[][] FASHION_ACTIONS = {
            {"Stand", "Pose", "Face", "Look", "Smile"}, {"Take", "Turn", "Walk", "Move"},
            {"Show", "Hold", "Point", "Bring"}, {"Turn", "Stand", "Pose", "Face"},
            {"Look", "Smile", "Pose", "Stand", "Wave"}};
    private static final String[][] PRODUCT_ACTIONS = {
            {"Hold", "Show", "Pose"}, {"Turn", "Lift", "Bring", "Show", "Tilt", "Move"},
            {"Show", "Hold", "Point", "Bring"}, {"Show", "Hold", "Lift", "Tilt"},
            {"Say", "Tell", "Share", "Give"}};
    private static final String[][] TALKING_ACTIONS = {
            {"Say", "Tell", "Share"}, {"Tell", "Explain", "Share", "Say"},
            {"Show", "Hold", "Point", "Bring"}, {"Explain", "Tell", "Share", "Say"},
            {"Share", "Say", "Tell", "Give"}};
    private static final String[][] INTRO_ACTIONS = {
            {"Say", "Tell", "Introduce"}, {"Tell", "Explain", "Show", "Share"},
            {"Show", "Hold", "Point", "Bring"}, {"Share", "Tell", "Say"},
            {"Welcome", "Wave", "Smile", "Say"}};
    // Conservative wording filter: also rejects benign "face the camera"; "look at the lens" is allowed.
    // This catches observed performer/operator drift, not arbitrary semantic errors or product claims.
    private static final Pattern FASHION_DEVICE_WORDS = Pattern.compile("\\b(camera|phone|screen|tripod)\\b", Pattern.CASE_INSENSITIVE);
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
        String[] roles = rolesForStyle(safeStyle);
        String[][] roleActions = actionsForRoles(roles);
        // Only our role/verb constants enter the grammar, never the creator's brief.
        String grammar = grammarForRoles(roles, roleActions);
        worker.execute(() -> {
            long began = SystemClock.elapsedRealtime();
            try {
                if (handle == 0) handle = nativeLoad(modelFile.getAbsolutePath());
                if (closed) return;
                String prompt = "<|im_start|>system\nDirect a creator making five recorded phone takes. Never give preparation or lighting setup as a take. " +
                        "The phone is already mounted and stationary. Address the creator as the performer with one imperative action, never describe a third person. " +
                        "The creator never holds, moves, adjusts or refocuses the filming device during a take. Keep body movements small. No aircraft actions. " +
                        "Use only colors, materials, features and product parts explicitly supplied in the brief; do not invent them. For an unspecified detail, ask the creator to choose one visible detail. " +
                        "Do not invent personal facts, product benefits or a verdict; ask for the creator's own honest words. " +
                        sceneBeats(safeStyle) + " Exact title order: " + String.join(", ", roles) + ". " +
                        "Return ONLY a JSON array of exactly five objects, each with title (the assigned exact title), instruction (one concrete sentence, at most 90 characters), caption (1 to 30 characters), duration_ms (3000 to 8000, whole seconds). " +
                        "Captions are human-readable story text, never durations, milliseconds, numeric labels or code. " +
                        "Allowed opening verbs per title: " + actionHints(roles, roleActions) + ". " +
                        "No markdown, no explanation.\n<|im_end|>\n<|im_start|>user\nScene: " + safeStyle + "\nBrief: " + safeBrief +
                        "\n<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
                String result = new String(nativeGenerate(handle, prompt.getBytes(StandardCharsets.UTF_8),
                        grammar.getBytes(StandardCharsets.UTF_8), 580), StandardCharsets.UTF_8);
                List<Shot> plan = parse(result, roles, roleActions);
                long elapsed = SystemClock.elapsedRealtime() - began;
                Log.i("MiniFilmLocalAI", "planner_complete model=qwen35_0.8b backend=cpu shots=" + plan.size() + " elapsed_ms=" + elapsed);
                main.post(() -> { if (!closed) listener.onPlan(plan, elapsed, MODEL_LABEL); });
            } catch (Exception | LinkageError failure) {
                Log.w("MiniFilmLocalAI", "planner_failed type=" + failure.getClass().getSimpleName());
                main.post(() -> { if (!closed) listener.onError("Local AI couldn't produce a valid plan. Use the editable template or try a shorter brief."); });
            } finally { busy.set(false); }
        });
    }

    private static List<Shot> parse(String text, String[] roles, String[][] roleActions) throws Exception {
        JSONArray array = new JSONArray(text.trim());
        if (array.length() != 5) throw new IllegalArgumentException("Expected five shots");
        List<Shot> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.getJSONObject(i);
            String title = row.getString("title");
            String instruction = row.getString("instruction");
            String caption = row.getString("caption");
            Object durationValue = row.get("duration_ms");
            if (!(durationValue instanceof Integer) && !(durationValue instanceof Long))
                throw new IllegalArgumentException("Expected integer duration");
            long duration = ((Number) durationValue).longValue();
            if (!title.equals(roles[i]) || instruction.length() > 90 || !startsWithAction(instruction, roleActions[i])
                    || caption.isEmpty() || caption.length() > 30 || duration < 3000 || duration > 8000)
                throw new IllegalArgumentException("Invalid shot");
            if (roles == FASHION_ROLES && FASHION_DEVICE_WORDS.matcher(instruction).find())
                throw new IllegalArgumentException("Fashion cue refers to a filming device");
            result.add(new Shot("ai-shot-" + (i + 1), title, instruction, caption, duration));
        }
        return result;
    }
    private static boolean startsWithAction(String instruction, String[] allowedVerbs) {
        for (String verb : allowedVerbs) {
            if (instruction.startsWith(verb + " ") && !instruction.substring(verb.length() + 1).trim().isEmpty())
                return true;
        }
        return false;
    }

    private static String[] rolesForStyle(String style) {
        String scene = style.toLowerCase(Locale.ROOT);
        if (scene.contains("product")) return PRODUCT_ROLES;
        if (scene.contains("intro")) return INTRO_ROLES;
        if (scene.contains("talk") || scene.contains("story")) return TALKING_ROLES;
        return FASHION_ROLES;
    }

    private static String[][] actionsForRoles(String[] roles) {
        if (roles == FASHION_ROLES) return FASHION_ACTIONS;
        if (roles == PRODUCT_ROLES) return PRODUCT_ACTIONS;
        if (roles == TALKING_ROLES) return TALKING_ACTIONS;
        if (roles == INTRO_ROLES) return INTRO_ACTIONS;
        return new String[][] {ACTION_VERBS, ACTION_VERBS, ACTION_VERBS, ACTION_VERBS, ACTION_VERBS};
    }

    private static String actionHints(String[] roles, String[][] roleActions) {
        if (roles != FASHION_ROLES && roles != PRODUCT_ROLES && roles != TALKING_ROLES && roles != INTRO_ROLES)
            return "every title=" + String.join("/", ACTION_VERBS);
        StringBuilder hints = new StringBuilder();
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) hints.append("; ");
            hints.append(roles[i]).append("=").append(String.join("/", roleActions[i]));
        }
        return hints.toString();
    }

    private static String grammarForRoles(String[] roles, String[][] roleActions) {
        StringBuilder grammar = new StringBuilder("root ::= \"[\" ws shot0 \",\" ws shot1 \",\" ws shot2 \",\" ws shot3 \",\" ws shot4 \"]\" ws\n");
        for (int i = 0; i < roles.length; i++) {
            grammar.append("shot").append(i).append(" ::= \"{\" ws ").append(grammarLiteral("\"title\""))
                    .append(" ws \":\" ws ").append(grammarLiteral("\"" + roles[i] + "\""))
                    .append(" ws \",\" ws ").append(grammarLiteral("\"instruction\""))
                    .append(" ws \":\" ws instruction").append(i).append(" \",\" ws fields\n");
        }
        grammar.append("fields ::= ").append(grammarLiteral("\"caption\""))
                .append(" ws \":\" ws caption \",\" ws ").append(grammarLiteral("\"duration_ms\""))
                .append(" ws \":\" ws duration \"}\" ws\n");
        for (int role = 0; role < roles.length; role++) {
            grammar.append("instruction").append(role).append(" ::= ").append(grammarLiteral("\""))
                    .append(" action").append(role).append(" ").append(grammarLiteral("\"")).append(" ws\n")
                    .append("action").append(role).append(" ::= ");
            for (int verb = 0; verb < roleActions[role].length; verb++) {
                if (verb > 0) grammar.append(" | ");
                String prefix = roleActions[role][verb] + " ";
                grammar.append(grammarLiteral(prefix)).append(" char{1,").append(90 - prefix.length()).append("}");
            }
            grammar.append("\n");
        }
        grammar.append("caption ::= ").append(grammarLiteral("\"")).append(" char{1,30} ")
                .append(grammarLiteral("\"")).append(" ws\n")
                .append("char ::= [^\"\\\\\\x7F\\x00-\\x1F]\n")
                .append("duration ::= (\"3000\" | \"4000\" | \"5000\" | \"6000\" | \"7000\" | \"8000\") ws\n")
                .append("ws ::= [ \\t\\n\\r]*\n");
        return grammar.toString();
    }

    private static String grammarLiteral(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    private static String clip(String value, int length) {
        String clean = value == null ? "" : value.trim();
        return clean.length() > length ? clean.substring(0, length) : clean;
    }

    private static String sceneBeats(String style) {
        String scene = style.toLowerCase(Locale.ROOT);
        if (scene.contains("product")) return "Reuse the actual product noun from THIS brief in the takes and captions. " +
                "Only explicitly stated colors, materials and parts may be named; a whole-product color does not specify a part's color. " +
                "Never supply texture, finish, quality, benefits, extra objects or a positive opinion not stated in the brief. " +
                "Detail asks the creator to choose a visible feature; In use asks for their actual use; Verdict asks their opinion rather than inventing it. " +
                "Example for a different brief, 'white ceramic cup'; replace its facts with THIS brief's facts:\n" +
                "[{\"title\":\"Hero\",\"instruction\":\"Hold your white ceramic cup and show it clearly.\",\"caption\":\"My ceramic cup\",\"duration_ms\":4000}," +
                "{\"title\":\"Reveal\",\"instruction\":\"Turn your cup slowly in your hands.\",\"caption\":\"The cup\",\"duration_ms\":4000}," +
                "{\"title\":\"Detail\",\"instruction\":\"Show one visible cup detail you choose.\",\"caption\":\"Cup detail\",\"duration_ms\":4000}," +
                "{\"title\":\"In use\",\"instruction\":\"Show one way you actually use your cup.\",\"caption\":\"How I use it\",\"duration_ms\":4000}," +
                "{\"title\":\"Verdict\",\"instruction\":\"Say your own honest one-line opinion of your cup.\",\"caption\":\"My opinion\",\"duration_ms\":4000}] ";
        if (scene.contains("intro")) return "Use THIS brief's actual name in Name, actual work noun in Work and Detail, and actual personal fact in Personal fact. " +
                "Never output generic placeholders such as 'supplied name' or 'stated work'. Do not invent affiliations, credentials, parts or another personal fact. " +
                "Example for a different brief, 'Mina bakes bread and likes hiking'; replace these facts with THIS brief's facts:\n" +
                "[{\"title\":\"Name\",\"instruction\":\"Say your name is Mina.\",\"caption\":\"Meet Mina\",\"duration_ms\":4000}," +
                "{\"title\":\"Work\",\"instruction\":\"Tell how you bake bread.\",\"caption\":\"I bake bread\",\"duration_ms\":4000}," +
                "{\"title\":\"Detail\",\"instruction\":\"Show one bread detail you choose.\",\"caption\":\"Bread details\",\"duration_ms\":4000}," +
                "{\"title\":\"Personal fact\",\"instruction\":\"Say you like hiking.\",\"caption\":\"I like hiking\",\"duration_ms\":4000}," +
                "{\"title\":\"Welcome\",\"instruction\":\"Welcome your audience with a smile.\",\"caption\":\"Welcome\",\"duration_ms\":4000}] ";
        if (scene.contains("talk") || scene.contains("story")) return "Reuse THIS brief's actual events and lesson in the speech cues and captions. " +
                "Do not invent story actions or events. Key idea explains the supplied decision about NEXT TIME; never turn that future decision into something that already happened. " +
                "A missed event does not establish access to its location. For Cutaway ask the creator to choose an object already available; neither instruction nor caption may name an unsupplied place, prop or action. " +
                "Example for a different brief, 'I forgot my keys, waited outside, then decided to check my bag'; replace these facts with THIS brief's facts:\n" +
                "[{\"title\":\"Hook\",\"instruction\":\"Say you forgot your keys.\",\"caption\":\"Forgot my keys\",\"duration_ms\":4000}," +
                "{\"title\":\"Context\",\"instruction\":\"Tell how you waited outside.\",\"caption\":\"Waiting outside\",\"duration_ms\":4000}," +
                "{\"title\":\"Cutaway\",\"instruction\":\"Show an available object you choose for your story.\",\"caption\":\"A story detail\",\"duration_ms\":4000}," +
                "{\"title\":\"Key idea\",\"instruction\":\"Explain your decision to check your bag next time.\",\"caption\":\"Next time plan\",\"duration_ms\":4000}," +
                "{\"title\":\"Takeaway\",\"instruction\":\"Share your plan to check your bag next time.\",\"caption\":\"My next step\",\"duration_ms\":4000}] ";
        return "The creator WEARS the outfit, never lays clothing on a table. Never mention camera, phone, screen or tripod in fashion instructions; use lens for eyeline. " +
                "Do not invent patterns, colors, materials, fasteners or garment features. Detail asks the creator to choose one visible garment detail. " +
                "Use complete short captions of 2 to 4 words, not sentence fragments. Caption examples in role order: Outfit hero; Small steps; Chosen detail; Side view; Final pose. " +
                "Adapt these phrasing examples to the brief: Hero pose: Stand wearing your outfit and hold your pose. " +
                "Movement: Take two small steps after checking your path. Detail: Show one visible detail you choose. " +
                "Side pose: Turn slightly sideways and hold your pose. Closing: Look at the lens and hold your pose.";
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
    private static native byte[] nativeGenerate(long handle, byte[] prompt, byte[] grammar, int maxTokens);
    private static native void nativeCancel(long handle);
    private static native void nativeFree(long handle);
}
