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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
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
    // English lexical checks, not semantic/safety validation. Camera mentions alone can be body
    // poses/eyelines. Detect bounded direct handling or hand placement; camera + "adjust it" is
    // conservatively treated as operation despite pronoun ambiguity. Other devices stay blocked.
    // Unlisted wording can escape detection; this does not prove a cue is useful or safe.
    private static final Pattern FASHION_DEVICE_WORDS = Pattern.compile(
            "\\b(phone|screen|tripod|drone|gimbal)s?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMERA_WORD = Pattern.compile("\\bcameras?\\b", Pattern.CASE_INSENSITIVE);
    private static final String CAMERA_OPERATOR_VERB =
            "(?:hold(?:s|ing)?|held|tak(?:e|es|ing|en)|bring(?:s|ing)?|brought|point(?:s|ing|ed)?|"
                    + "focus(?:es|ing|ed)?|mov(?:e|es|ing|ed)|adjust(?:s|ing|ed)?|refocus(?:es|ing|ed)?|"
                    + "reposition(?:s|ing|ed)?|lift(?:s|ing|ed)?|carr(?:y|ies|ying|ied)|aim(?:s|ing|ed)?|"
                    + "tilt(?:s|ing|ed)?|pan(?:s|ning|ned)?|rotat(?:e|es|ing|ed)|rais(?:e|es|ing|ed)|"
                    + "lower(?:s|ing|ed)?|turn(?:s|ing|ed)?)";
    private static final Pattern CAMERA_DIRECT_OPERATION = Pattern.compile(
            "\\b" + CAMERA_OPERATOR_VERB
                    + "\\s+(?:(?:a|an|the|your|my|our|their|this|that|its|filming|recording|front|rear|main)\\s+){0,3}cameras?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMERA_HAND_PLACEMENT = Pattern.compile(
            "\\bcameras?\\s+(?:(?:is|are|being|kept)\\s+){0,2}(?:held|carried|handheld|"
                    + "in\\s+(?:(?:a|an|the|your|my|our|their|one|both)\\s+){0,3}hands?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMERA_PRONOUN_OPERATION = Pattern.compile(
            "\\b" + CAMERA_OPERATOR_VERB + "\\s+(?:it|this|that)\\b", Pattern.CASE_INSENSITIVE);
    // Narrow English lexical guard for observed Movement drafts: taking a garment part or
    // treating one as a walking/turning destination. It is not a semantic or safety validator.
    // Inspect only the initial object/action: later steps cannot exempt taking a garment part,
    // and a later show/point gesture is not the first action's destination. Unlisted English
    // paraphrases/compound clauses can escape this bounded check; later clauses are not validated.
    private static final String GARMENT_PART = "(?:pocket|lapel|collar|button|zipper|buckle|hem|sleeve)s?";
    private static final String PART_NOUN_MODIFIERS =
            "(?:(?!(?:steps?|then|and|while|before|after|to|show|point|hold|pose|turn|look|face|walk|move|take)\\b)[a-z'-]+\\s+){0,6}";
    private static final Pattern FASHION_TAKE_PART = Pattern.compile(
            "^take\\s+" + PART_NOUN_MODIFIERS + GARMENT_PART + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FASHION_PART_DESTINATION = Pattern.compile(
            "^(?:(?:walk|move|turn)\\b|take\\s+"
                    + "(?:(?!(?:then|and|while|before|after|to|toward|towards|into|show|point|hold|pose|turn|look|face|walk|move|take)\\b)[a-z0-9'-]+\\s+){0,4}steps?\\b)"
                    + "(?:(?!\\b(?:and|then|while|before|after|show|point|hold|pose|look|face|take|turn|walk|move)\\b)[^.;,!?])*?"
                    + "\\b(?:to|toward|towards|into)\\s+" + PART_NOUN_MODIFIERS
                    + GARMENT_PART + "\\b(?=\\s*(?:$|[.,;!?]|(?:and|then|while|before|after)\\b))",
            Pattern.CASE_INSENSITIVE);
    private static final class FashionMovementException extends IllegalArgumentException {
        FashionMovementException() { super("Invalid fashion movement"); }
    }
    private static final class FashionCaptionException extends IllegalArgumentException {
        FashionCaptionException() { super("Incomplete fashion caption label"); }
    }
    private static final String REVIEWED_MARKER = "Creator-reviewed reference moments:";
    private static final Pattern TIMED_REVIEW_CHUNK = Pattern.compile("^([0-2]):([0-5][0-9])(?:\\.([0-9]{3}))? (\\S(?:.*\\S)?)$");
    private static final Pattern NAMED_REVIEW_CUE = Pattern.compile("^(Hero pose|Hero|Movement|Detail|Side pose|Closing)\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAPTION_CLOCK = Pattern.compile("[0-9]{1,3}:[0-5][0-9](?:\\.[0-9]{1,3})?");
    // Without a named reviewed Detail cue, every fashion plan uses creator-choice requests.
    // Ordinary free-text features are not extracted/verified. The creator can name a Detail in
    // a reviewed board or edit the resulting shot. This is not learned feature selection.
    private static final String[] CREATOR_DETAIL_INSTRUCTIONS = {
            "Show one visible garment detail you choose.", "Point to one visible garment detail you choose."};
    private static final String[] CREATOR_DETAIL_CAPTIONS = {"Chosen detail", "Your garment detail", "Visible detail"};
    // Every talking-story Cutaway asks for an available object chosen by the creator. Specific
    // free-text cutaway props/events are not extracted or verified; the creator can edit the shot.
    // This closed native choice is disclosed, not learned selection of a story's physical prop.
    private static final String[] CREATOR_CUTAWAY_INSTRUCTIONS = {
            "Show an available object you choose for your story.",
            "Hold an available object you choose for your story."};
    private static final String[] CREATOR_CUTAWAY_CAPTIONS = {
            "A story detail", "Chosen story object", "Your story detail"};
    private static boolean runtimeLoaded;
    static { try { System.loadLibrary("director_llm"); runtimeLoaded = true; } catch (LinkageError ignored) { } }
    private final File modelFile;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile long handle;
    private volatile boolean closed;
    // Owned by the worker; retained while handle exists, even between planning requests.
    private LocalModelLease.Token modelLease;
    private static final class ReviewedCues {
        final boolean present;
        final String[] instructions = new String[5];
        ReviewedCues(boolean present) { this.present = present; }
        boolean hasAny() {
            for (String instruction : instructions) if (instruction != null) return true;
            return false;
        }
    }

    public LocalPlanner(Context context) { modelFile = new File(context.getFilesDir(), "director-model.gguf"); }
    public boolean isModelAvailable() { return runtimeLoaded && modelFile.isFile() && modelFile.length() > 10000000; }

    public void generate(String brief, String style, Listener listener) {
        if (closed || !isModelAvailable()) { listener.onError("Local model isn't installed. Use the editable template."); return; }
        if (!busy.compareAndSet(false, true)) { listener.onError("The local planner is already working."); return; }
        String safeBrief = clip(brief, 500).replace("<|", "").replace("|>", "");
        String safeStyle = clip(style, 60).replace("<|", "").replace("|>", "");
        String[] roles = rolesForStyle(safeStyle);
        String[][] roleActions = actionsForRoles(roles);
        final ReviewedCues reviewedCues;
        try {
            String originalBrief = brief == null ? "" : brief.trim();
            if (originalBrief.contains(REVIEWED_MARKER) && originalBrief.length() > 500)
                throw new IllegalArgumentException("Shorten the brief and reviewed reference notes to 500 characters together.");
            // Parse the original before chat-token sanitation, so retained creator wording is never silently altered.
            reviewedCues = parseReviewedCues(originalBrief, roles);
        } catch (IllegalArgumentException failure) {
            busy.set(false);
            main.post(() -> { if (!closed) listener.onError(failure.getMessage()); });
            return;
        }
        boolean reviewedMoments = reviewedCues.present;
        int detailIndex = indexOfRole(roles, "Detail");
        boolean constrainDetail = roles == FASHION_ROLES && reviewedCues.instructions[detailIndex] == null;
        // Only our role/verb constants enter the grammar, never the creator's brief.
        String grammar = grammarForRoles(roles, roleActions, constrainDetail);
        try { worker.execute(() -> {
            long began = SystemClock.elapsedRealtime();
            try {
                if (closed) return;
                if (handle == 0) {
                    modelLease = LocalModelLease.acquire("planner", () -> closed, 5000);
                    if (closed) return;
                    long loaded = nativeLoad(modelFile.getAbsolutePath());
                    synchronized (this) {
                        handle = loaded;
                        if (closed && handle != 0) nativeCancel(handle);
                    }
                }
                if (closed) return;
                String prompt = buildPrompt(safeBrief, safeStyle, roles, roleActions, reviewedMoments, constrainDetail, true);
                if (closed) return;
                String result = new String(nativeGenerate(handle, prompt.getBytes(StandardCharsets.UTF_8),
                        grammar.getBytes(StandardCharsets.UTF_8), 580), StandardCharsets.UTF_8);
                List<Shot> plan = parse(result, roles, roleActions, constrainDetail);
                // Native output is still a real five-shot draft. Named instructions below are creator-authored
                // authoritative edits, not evidence that the model understood or preserved the reviewed direction.
                for (int i = 0; i < plan.size(); i++) if (reviewedCues.instructions[i] != null) {
                    Shot generated = plan.get(i);
                    plan.set(i, new Shot(generated.id, generated.title, reviewedCues.instructions[i],
                            generated.caption, generated.targetDurationMs));
                }
                long elapsed = SystemClock.elapsedRealtime() - began;
                Log.i("MiniFilmLocalAI", "planner_complete model=qwen35_0.8b backend=cpu shots=" + plan.size() + " elapsed_ms=" + elapsed);
                String modelLabel = MODEL_LABEL + (constrainDetail ? " · creator-choice detail constraint" : "")
                        + (roles == TALKING_ROLES ? " · creator-choice cutaway constraint" : "")
                        + (reviewedCues.hasAny() ? " · creator-authored reviewed cues retained" : "");
                main.post(() -> { if (!closed) listener.onPlan(plan, elapsed, modelLabel); });
            } catch (Exception | LinkageError failure) {
                boolean invalidMovement = failure instanceof FashionMovementException;
                boolean invalidCaption = failure instanceof FashionCaptionException;
                // Fixed category only: never log the brief, generated text or exception message.
                Log.w("MiniFilmLocalAI", invalidMovement ? "planner_failed category=fashion_movement"
                        : invalidCaption ? "planner_failed category=fashion_caption_labels"
                        : "planner_failed type=" + failure.getClass().getSimpleName());
                String message = invalidMovement
                        ? "Local AI proposed an unusable movement. Keep your current plan or use the editable starter."
                        : invalidCaption ? "Local AI proposed incomplete fashion captions. Keep your current plan or use the editable starter."
                        : "Local AI couldn't produce a valid plan. Use the editable template or try a shorter brief.";
                main.post(() -> { if (!closed) listener.onError(message); });
            } finally {
                if (closed || handle == 0) freeNativeAndRelease();
                busy.set(false);
            }
        }); } catch (RejectedExecutionException failure) {
            busy.set(false);
            if (!closed) listener.onError("The local planner is closed.");
        }
    }

    /** Shared production prompt; only the explicit chat adapter changes for a comparison model. */
    private static String buildPrompt(String safeBrief, String safeStyle, String[] roles, String[][] roleActions,
            boolean reviewedMoments, boolean constrainDetail, boolean emptyThinkAdapter) {
        return "<|im_start|>system\nDirect a creator making five recorded phone takes. Never give preparation or lighting setup as a take. " +
                "The phone is already mounted and stationary. Address the creator as the performer with one imperative action, never describe a third person. " +
                "The creator never holds, moves, adjusts or refocuses the filming device during a take. Keep body movements small. No aircraft actions. " +
                "Use only colors, materials, features and product parts explicitly supplied in the brief; do not invent them. For an unspecified detail, ask the creator to choose one visible detail. " +
                "Do not invent personal facts, product benefits or a verdict; ask for the creator's own honest words. " +
                sceneBeats(safeStyle, reviewedMoments)
                + (constrainDetail ? " No explicit creator-reviewed Detail cue was supplied. For Detail ask the creator to choose one visible garment detail; its caption must be generic, without naming a garment part. " : "")
                + " Exact title order: " + String.join(", ", roles) + ". " +
                "Return ONLY a JSON array of exactly five objects, each with title (the assigned exact title), instruction (one concrete sentence, at most 90 characters), caption (1 to 30 characters), duration_ms (3000 to 8000, whole seconds). " +
                "Creator-reviewed reference moments are shot cues. Source timestamps describe the reference only: never copy them into captions or use them as shot lengths. " +
                "When reviewed notes name a shot role, preserve its specified direction or pose in that role instead of a generic substitute. " +
                "Captions are short human-readable story phrases starting with a letter, never source timestamps, durations, milliseconds, numeric labels or code. " +
                "Allowed opening verbs per title: " + actionHints(roles, roleActions) + ". " +
                "No markdown, no explanation.\n<|im_end|>\n<|im_start|>user\nScene: " + safeStyle + "\nBrief: " + safeBrief +
                "\n<|im_end|>\n<|im_start|>assistant\n" +
                (emptyThinkAdapter ? "<think>\n\n</think>\n\n" : "");
    }

    private static List<Shot> parse(String text, String[] roles, String[][] roleActions, boolean constrainDetail) throws Exception {
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
                    || caption.isEmpty() || caption.length() > 30 || !Character.isLetter(caption.charAt(0))
                    || CAPTION_CLOCK.matcher(caption).find()
                    || duration < 3000 || duration > 8000)
                throw new IllegalArgumentException("Invalid shot");
            if (roles == FASHION_ROLES && invalidFashionDeviceCue(instruction))
                throw new IllegalArgumentException("Fashion cue refers to a filming device");
            if (roles == FASHION_ROLES && letterCount(caption) < 2)
                throw new FashionCaptionException();
            if (roles == FASHION_ROLES && i == 1 && invalidFashionMovement(instruction))
                throw new FashionMovementException();
            if (constrainDetail && i == 2 && (!contains(CREATOR_DETAIL_INSTRUCTIONS, instruction)
                    || !contains(CREATOR_DETAIL_CAPTIONS, caption)))
                throw new IllegalArgumentException("Unspecified detail must remain a creator choice");
            if (roles == TALKING_ROLES && i == 2 && (!contains(CREATOR_CUTAWAY_INSTRUCTIONS, instruction)
                    || !contains(CREATOR_CUTAWAY_CAPTIONS, caption)))
                throw new IllegalArgumentException("Talking cutaway must remain a creator choice");
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
    private static boolean contains(String[] values, String value) {
        for (String allowed : values) if (allowed.equals(value)) return true;
        return false;
    }

    private static boolean invalidFashionMovement(String instruction) {
        return FASHION_TAKE_PART.matcher(instruction).find() || FASHION_PART_DESTINATION.matcher(instruction).find();
    }

    private static boolean invalidFashionDeviceCue(String instruction) {
        if (FASHION_DEVICE_WORDS.matcher(instruction).find()) return true;
        return CAMERA_WORD.matcher(instruction).find()
                && (CAMERA_DIRECT_OPERATION.matcher(instruction).find()
                    || CAMERA_HAND_PLACEMENT.matcher(instruction).find()
                    || CAMERA_PRONOUN_OPERATION.matcher(instruction).find());
    }

    private static int letterCount(String caption) {
        int letters = 0;
        for (int i = 0; i < caption.length(); i++) if (Character.isLetter(caption.charAt(i))) letters++;
        return letters;
    }

    private static ReviewedCues parseReviewedCues(String brief, String[] roles) {
        int marker = brief.indexOf(REVIEWED_MARKER);
        ReviewedCues result = new ReviewedCues(marker >= 0);
        if (marker < 0) return result;
        if (brief.indexOf(REVIEWED_MARKER, marker + REVIEWED_MARKER.length()) >= 0)
            throw new IllegalArgumentException("Keep one reviewed reference notes section.");
        String summary = brief.substring(marker + REVIEWED_MARKER.length()).trim();
        if (summary.isEmpty() || summary.length() > ReferenceBoard.MAX_SUMMARY
                || !summary.equals(summary.replaceAll("\\s+", " ").trim()))
            throw new IllegalArgumentException("Review the reference notes again; use short notes with their original times.");
        for (int i = 0; i < summary.length(); i++) if (Character.isISOControl(summary.charAt(i)))
            throw new IllegalArgumentException("Remove unsupported characters from the reviewed reference notes.");
        // Ordinary semicolons belong to creator notes. A semicolon followed by a clock and space
        // is the board's moment boundary; a note containing that same syntax is inherently ambiguous.
        String[] chunks = summary.split("; (?=[0-9]+:[0-9]{2}(?:\\.[0-9]+)? )", -1);
        if (chunks.length < 1 || chunks.length > 3)
            throw new IllegalArgumentException("Use one to three timed reference moments.");
        long previous = -1;
        for (String chunk : chunks) {
            Matcher timed = TIMED_REVIEW_CHUNK.matcher(chunk);
            if (!timed.matches()) throw new IllegalArgumentException("Review reference times in m:ss or m:ss.mmm format.");
            long time = Long.parseLong(timed.group(1)) * 60_000 + Long.parseLong(timed.group(2)) * 1000
                    + (timed.group(3) == null ? 0 : Long.parseLong(timed.group(3)));
            if (time < 0 || time > 179_999 || time <= previous)
                throw new IllegalArgumentException("Reference moments must have distinct times in their original order.");
            previous = time;
            Matcher named = NAMED_REVIEW_CUE.matcher(timed.group(4));
            if (!named.matches()) continue; // Observations without an explicit role remain context, never manual commands.
            String role = named.group(1);
            if (role.equalsIgnoreCase("Hero")) role = "Hero pose";
            int index = indexOfRole(roles, role);
            if (index < 0 && role.equalsIgnoreCase("Hero pose")) index = indexOfRole(roles, "Hero");
            if (index < 0) throw new IllegalArgumentException("The reviewed role '" + role + "' is not part of this scene. Edit the reference note.");
            if (result.instructions[index] != null)
                throw new IllegalArgumentException("Keep one reviewed cue per shot role.");
            String cue = named.group(2);
            boolean imperative = false;
            for (String verb : ACTION_VERBS) if (cue.length() > verb.length() + 1
                    && cue.regionMatches(true, 0, verb + " ", 0, verb.length() + 1)) { imperative = true; break; }
            if (cue.length() > 90 || !imperative || cue.contains("<|") || cue.contains("|>")
                    || cue.indexOf('{') >= 0 || cue.indexOf('}') >= 0)
                throw new IllegalArgumentException("Use one short action for each reviewed role, such as Stand, Face, Pause, Show or Turn.");
            for (int i = 0; i < cue.length(); i++) if (Character.isISOControl(cue.charAt(i)))
                throw new IllegalArgumentException("Remove unsupported characters from the reviewed cue.");
            if (roles == FASHION_ROLES && invalidFashionDeviceCue(cue))
                throw new IllegalArgumentException("Keep fashion cues about your performance; leave filming devices mounted and remove device-handling actions.");
            if (roles == FASHION_ROLES && index == 1 && invalidFashionMovement(cue))
                throw new IllegalArgumentException("Correct the reviewed Movement cue: use a small body turn or one or two steps; keep garment parts for Detail.");
            result.instructions[index] = cue; // Exact creator wording; role-specific model verbs do not constrain manual edits.
        }
        return result;
    }
    private static int indexOfRole(String[] roles, String role) {
        for (int i = 0; i < roles.length; i++) if (roles[i].equalsIgnoreCase(role)) return i;
        return -1;
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

    private static String grammarForRoles(String[] roles, String[][] roleActions, boolean constrainDetail) {
        StringBuilder grammar = new StringBuilder("root ::= \"[\" ws shot0 \",\" ws shot1 \",\" ws shot2 \",\" ws shot3 \",\" ws shot4 \"]\" ws\n");
        for (int i = 0; i < roles.length; i++) {
            grammar.append("shot").append(i).append(" ::= \"{\" ws ").append(grammarLiteral("\"title\""))
                    .append(" ws \":\" ws ").append(grammarLiteral("\"" + roles[i] + "\""))
                    .append(" ws \",\" ws ").append(grammarLiteral("\"instruction\""))
                    .append(" ws \":\" ws instruction").append(i).append(" \",\" ws ")
                    .append(i == 2 && roles == TALKING_ROLES ? "cutawayFields"
                            : constrainDetail && i == 2 ? "detailFields" : "fields").append("\n");
        }
        grammar.append("fields ::= ").append(grammarLiteral("\"caption\""))
                .append(" ws \":\" ws caption \",\" ws ").append(grammarLiteral("\"duration_ms\""))
                .append(" ws \":\" ws duration \"}\" ws\n");
        if (constrainDetail) {
            grammar.append("detailFields ::= ").append(grammarLiteral("\"caption\""))
                    .append(" ws \":\" ws detailCaption \",\" ws ").append(grammarLiteral("\"duration_ms\""))
                    .append(" ws \":\" ws duration \"}\" ws\n")
                    .append("detailCaption ::= (").append(quotedChoices(CREATOR_DETAIL_CAPTIONS)).append(") ws\n");
        }
        if (roles == TALKING_ROLES) {
            grammar.append("cutawayFields ::= ").append(grammarLiteral("\"caption\""))
                    .append(" ws \":\" ws cutawayCaption \",\" ws ").append(grammarLiteral("\"duration_ms\""))
                    .append(" ws \":\" ws duration \"}\" ws\n")
                    .append("cutawayCaption ::= (").append(quotedChoices(CREATOR_CUTAWAY_CAPTIONS)).append(") ws\n");
        }
        for (int role = 0; role < roles.length; role++) {
            if (roles == TALKING_ROLES && role == 2) {
                grammar.append("instruction2 ::= (").append(quotedChoices(CREATOR_CUTAWAY_INSTRUCTIONS)).append(") ws\n");
                continue;
            }
            if (constrainDetail && role == 2) {
                grammar.append("instruction2 ::= (").append(quotedChoices(CREATOR_DETAIL_INSTRUCTIONS)).append(") ws\n");
                continue;
            }
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
        grammar.append("caption ::= ").append(grammarLiteral("\"")).append(" captionStart char{0,29} ")
                .append(grammarLiteral("\"")).append(" ws\n")
                .append("captionStart ::= [a-zA-Z]\n")
                .append("char ::= [^\"\\\\\\x7F\\x00-\\x1F]\n")
                .append("duration ::= (\"3000\" | \"4000\" | \"5000\" | \"6000\" | \"7000\" | \"8000\") ws\n")
                .append("ws ::= [ \\t\\n\\r]{0,8}\n");
        return grammar.toString();
    }

    private static String quotedChoices(String[] values) {
        StringBuilder alternatives = new StringBuilder();
        for (String value : values) {
            if (alternatives.length() > 0) alternatives.append(" | ");
            alternatives.append(grammarLiteral("\"" + value + "\""));
        }
        return alternatives.toString();
    }

    private static String grammarLiteral(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    private static String clip(String value, int length) {
        String clean = value == null ? "" : value.trim();
        return clean.length() > length ? clean.substring(0, length) : clean;
    }

    private static String sceneBeats(String style, boolean reviewedMoments) {
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
        return "Fashion: wear the outfit. Use lens for eyeline; never handle the filming device. " +
                "Invent no garment colors, patterns, materials or parts. Movement is a small body turn or one/two steps after checking the path, never taking a garment part or moving toward one. Parts belong to Detail. " +
                "Captions are complete 2-4 word phrases, never single-letter labels. " +
                (reviewedMoments ? "Retain every creator-reviewed named pose/direction. " : "") +
                "Adapt this generic example to THIS brief; preserve reviewed manual poses instead of copying example poses:\n" +
                "[{\"title\":\"Hero pose\",\"instruction\":\"Stand wearing your outfit.\",\"caption\":\"My outfit\",\"duration_ms\":4000}," +
                "{\"title\":\"Movement\",\"instruction\":\"Take two small steps.\",\"caption\":\"Small steps\",\"duration_ms\":4000}," +
                "{\"title\":\"Detail\",\"instruction\":\"Show one visible garment detail you choose.\",\"caption\":\"Chosen detail\",\"duration_ms\":4000}," +
                "{\"title\":\"Side pose\",\"instruction\":\"Turn slightly sideways.\",\"caption\":\"Side view\",\"duration_ms\":4000}," +
                "{\"title\":\"Closing\",\"instruction\":\"Look at the lens and hold your pose.\",\"caption\":\"Final pose\",\"duration_ms\":4000}] ";
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        // Native handle is only freed on the same worker after generation finishes.
        long current = handle;
        if (current != 0) nativeCancel(current);
        worker.execute(this::freeNativeAndRelease);
        worker.shutdown();
    }
    private synchronized void freeNativeAndRelease() {
        if (handle != 0) { nativeFree(handle); handle = 0; }
        if (modelLease != null) { modelLease.close(); modelLease = null; }
    }
    private static native long nativeLoad(String path);
    private static native byte[] nativeGenerate(long handle, byte[] prompt, byte[] grammar, int maxTokens);
    private static native void nativeCancel(long handle);
    private static native void nativeFree(long handle);
}
