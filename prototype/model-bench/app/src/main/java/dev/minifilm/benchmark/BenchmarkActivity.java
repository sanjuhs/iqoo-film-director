package dev.minifilm.benchmark;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Permission-free pre-event CPU text timing experiment; no STT/TTS/VLM. */
public final class BenchmarkActivity extends Activity {
    static { System.loadLibrary("model_bench"); }
    private static native long nativeCreate();
    private static native void nativeBegin(long handle);
    private static native void nativeLoad(long handle, String filename);
    private static native byte[] nativeRun(long handle, byte[] prompt);
    private static native double[] nativeMetrics(long handle);
    private static native void nativeCancel(long handle);
    private static native void nativeClose(long handle);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long handle;
    private boolean busy, loaded, destroyed;
    private int remainingFixtureRuns;
    private EditText prompt;
    private Button run, stop;
    private TextView status, result, reply;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        handle = nativeCreate();
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(32), dp(22), dp(28));
        body.setBackgroundColor(Color.rgb(250, 248, 243));
        scroll.addView(body);
        label(body, "Mini Film · Model test", 27);
        label(body, "Pre-event research · text only · CPU", 15);
        label(body, "Measure one short Qwen reply. No microphone, camera, network, speech models or vision are running.", 16);
        label(body, "4 CPU threads · 1,024 context tokens · at most 32 reply tokens · greedy sampling", 14);
        prompt = new EditText(this);
        prompt.setText("Suggest one pose for a jacket reveal.");
        prompt.setHint("Ask for one short filming cue");
        prompt.setContentDescription("Benchmark prompt");
        prompt.setMinLines(2); prompt.setMaxLines(5);
        body.addView(prompt, new LinearLayout.LayoutParams(-1, -2));
        run = new Button(this); run.setText("Run benchmark");
        run.setContentDescription("Run benchmark");
        body.addView(run, new LinearLayout.LayoutParams(-1, dp(56)));
        stop = new Button(this); stop.setText("Stop"); stop.setEnabled(false);
        stop.setContentDescription("Stop benchmark");
        body.addView(stop, new LinearLayout.LayoutParams(-1, dp(52)));
        status = label(body, modelFile().isFile() ? "Model file ready. First run includes loading." : "Model missing. Add director-model.gguf to this app's private files before running.", 16);
        result = label(body, "Run again after the first result to measure a warm model. Each run clears the prompt cache.", 16);
        result.setTextIsSelectable(true);
        reply = label(body, "", 17); reply.setTextIsSelectable(true);
        label(body, "Timing results stay in this app's private files. Prompts and replies are not saved in the timing report or printed to logs.", 13);
        scroll.setFitsSystemWindows(true);
        setContentView(scroll);
        run.setOnClickListener(v -> { remainingFixtureRuns = 0; benchmark(); });
        stop.setOnClickListener(v -> cancel());
        maybeRunFixture(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); maybeRunFixture(intent);
    }
    private void maybeRunFixture(Intent intent) {
        // Explicit debug CLI hook runs only this public synthetic prompt. A
        // normal launch stays idle; no capture, permissions or user data read.
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0 ||
                intent == null || !intent.getBooleanExtra("benchmark_once", false) || busy) return;
        remainingFixtureRuns = Math.max(1, Math.min(5, intent.getIntExtra("benchmark_runs", 1)));
        prompt.setText("Suggest one pose for a jacket reveal.");
        prompt.post(this::benchmark);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(LinearLayout parent, String value, int size) {
        TextView label = new TextView(this);
        label.setText(value); label.setTextSize(size); label.setTextColor(Color.rgb(38, 38, 33));
        label.setPadding(0, dp(7), 0, dp(9));
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }
    private File modelFile() { return new File(getFilesDir(), "director-model.gguf"); }
    private void benchmark() {
        if (busy || destroyed) return;
        final String text = prompt.getText().toString().trim();
        if (text.isEmpty() || text.length() > 500) { status.setText("Use a short prompt of 1–500 characters."); return; }
        if (!modelFile().isFile()) { status.setText("Model file is missing. No download is built into this app."); return; }
        if (remainingFixtureRuns > 0) --remainingFixtureRuns;
        busy = true; nativeBegin(handle);
        run.setEnabled(false); prompt.setEnabled(false); stop.setEnabled(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText(loaded ? "Measuring a warm reply…" : "Loading the local model, then measuring a reply…");
        reply.setText("");
        worker.execute(() -> {
            final long started = SystemClock.elapsedRealtimeNanos();
            try {
                final boolean wasLoaded = loaded;
                if (!loaded) { nativeLoad(handle, modelFile().getAbsolutePath()); loaded = true; }
                final byte[] bytes = nativeRun(handle, text.getBytes(StandardCharsets.UTF_8));
                final double[] m = nativeMetrics(handle);
                if (m == null || m.length != 14 || bytes == null) throw new IllegalStateException("No timing result returned.");
                final double buttonMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0;
                final JSONObject data = report(m, text, buttonMs, wasLoaded);
                writeReport(data);
                final String summary = format(m, buttonMs, wasLoaded);
                final String output = new String(bytes, StandardCharsets.UTF_8);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    status.setText(m[10] == 1 ? "Stopped. Partial timings saved." : m[13] == 2 ? "32-token limit reached. Reply may be incomplete; timings saved." : "Finished. Timing report saved locally.");
                    result.setText(summary);
                    reply.setText(output.isBlank() ? "No reply text generated." : "Model reply\n" + output);
                    finishUi();
                    if (m[10] == 0 && remainingFixtureRuns > 0) benchmark();
                });
            } catch (Exception failure) {
                // Generic, controlled failures only; never expose a prompt in logs.
                final String message = failure instanceof IllegalStateException ? failure.getMessage() : "Could not save the local timing report.";
                runOnUiThread(() -> { if (!destroyed) { remainingFixtureRuns = 0; status.setText(message); finishUi(); } });
            }
        });
    }
    private JSONObject report(double[] m, String text, double buttonMs, boolean wasLoaded) throws Exception {
        JSONObject data = new JSONObject();
        data.put("schema_version", 1);
        data.put("measured_at_utc", Instant.now().toString());
        data.put("experiment", "pre_event_qwen_text_cpu_only");
        data.put("runtime_commit", "11fe02151f79c41d0d4af7da708755d73b9c0da6");
        data.put("model_filename", "director-model.gguf");
        data.put("model_bytes", modelFile().length());
        data.put("phone_model", Build.MODEL);
        data.put("android_sdk", Build.VERSION.SDK_INT);
        data.put("cpu_arch", "armv8.2-a+dotprod+fp16");
        data.put("prompt_sha256", sha256(text));
        data.put("max_output_tokens", 32);
        data.put("greedy", true);
        data.put("model_reused", wasLoaded);
        data.put("model_load_once_ms", m[0]);
        data.put("model_load_this_run_ms", wasLoaded ? 0 : m[0]);
        data.put("template_and_tokenization_ms", m[1]);
        data.put("prefill_ms", m[2]);
        data.put("first_token_ms", m[7] > 0 ? m[3] : JSONObject.NULL);
        data.put("decode_between_first_and_last_token_ms", m[4]);
        data.put("inference_total_ms", m[5]);
        data.put("button_to_result_ms", buttonMs);
        data.put("prompt_tokens", (int) m[6]);
        data.put("output_tokens", (int) m[7]);
        data.put("decoded_output_tokens", (int) m[8]);
        data.put("warm_inference", m[9] == 1);
        data.put("cancelled_or_deadline", m[10] == 1);
        data.put("stop_reason", m[13] == 1 ? "eog" : m[13] == 2 ? "token_limit" : "cancel_or_deadline");
        data.put("output_complete", m[13] == 1);
        data.put("context_tokens", (int) m[11]);
        data.put("cpu_threads", (int) m[12]);
        data.put("decode_tokens_per_second", m[7] > 1 && m[4] > 0 ? (m[7] - 1) * 1000 / m[4] : JSONObject.NULL);
        data.put("timing_boundary", "first_token starts before chat formatting; decode measures first-to-last emitted non-EOG token; button time excludes result serialization/write");
        return data;
    }
    private String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        for (byte b : digest) value.append(String.format(Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }
    private void writeReport(JSONObject data) throws Exception {
        byte[] json = data.toString(2).getBytes(StandardCharsets.UTF_8);
        File temporary = new File(getFilesDir(), "benchmark-result.tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary)) { stream.write(json); stream.getFD().sync(); }
        File target = new File(getFilesDir(), "benchmark-result.json");
        if (!temporary.renameTo(target)) throw new IllegalStateException("Could not replace the local timing report.");
        try (FileOutputStream stream = new FileOutputStream(new File(getFilesDir(), "benchmark-history.jsonl"), true)) {
            stream.write((data.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
    private String format(double[] m, double buttonMs, boolean wasLoaded) {
        final String first = m[7] > 0 ? String.format(Locale.ROOT, "%.2f s", m[3] / 1000) : "No token";
        return String.format(Locale.ROOT,
            "%s\nModel load: %.2f s%s\nFirst token: %s\nPrefill: %.2f s · %d input tokens\nDecode: %.2f s · %d output tokens\nGeneration total: %.2f s\nButton to result: %.2f s\nDecode rate: %s\nCompletion: %s\n\nReport: benchmark-result.json",
            m[9] == 1 ? "Warm inference · prompt cache cleared" : "First inference · prompt cache cleared",
            m[0] / 1000, wasLoaded ? " (previously loaded)" : "", first,
            m[2] / 1000, (int) m[6], m[4] / 1000, (int) m[7], m[5] / 1000, buttonMs / 1000,
            m[7] > 1 && m[4] > 0 ? String.format(Locale.ROOT, "%.2f tokens/s", (m[7] - 1) * 1000 / m[4]) : "Not measurable",
            m[13] == 1 ? "EOG (quality unverified)" : m[13] == 2 ? "Token limit; potentially truncated" : "Cancelled/deadline");
    }
    private void cancel() {
        remainingFixtureRuns = 0;
        if (!busy || destroyed) return;
        nativeCancel(handle);
        stop.setEnabled(false);
        status.setText("Stopping at the next safe model boundary…");
    }
    private void finishUi() {
        busy = false; run.setEnabled(true); prompt.setEnabled(true); stop.setEnabled(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStop() { cancel(); super.onStop(); }
    @Override protected void onDestroy() {
        destroyed = true; nativeCancel(handle);
        final long closing = handle;
        // Close queues after any active model work. JNI also retains shared
        // ownership during calls, so Cancel/close cannot free a live context.
        worker.execute(() -> nativeClose(closing));
        worker.shutdown();
        super.onDestroy();
    }
}
