// New pre-event measurement harness, 5 October 2026. llama.cpp API: MIT.
#include <jni.h>
#include "llama.h"
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include <algorithm>
#include <sys/auxv.h>
#include <asm/hwcap.h>

using Clock = std::chrono::steady_clock;
static double elapsed(Clock::time_point from, Clock::time_point to) {
    return std::chrono::duration<double, std::milli>(to - from).count();
}
struct State {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::atomic<bool> cancelled{false};
    Clock::time_point deadline;
    // load, template/tokenization, prefill, first-token, decode, total, prompt,
    // generated, decoded-output-tokens, warm-run, stopped, context, threads,
    // stop-reason: 1 EOG, 2 token cap, 3 cancellation/deadline.
    double metrics[14]{};
    bool ran = false;
    ~State() { if (context) llama_free(context); if (model) llama_model_free(model); }
};
static std::mutex registry_mutex;
static std::unordered_map<jlong, std::shared_ptr<State>> registry;
static std::atomic<jlong> next_id{1};
static std::once_flag backend_once;
static std::shared_ptr<State> get(jlong id) {
    std::lock_guard<std::mutex> guard(registry_mutex);
    auto it = registry.find(id);
    return it == registry.end() ? nullptr : it->second;
}
static void fail(JNIEnv * env, const char * text) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), text);
}
static bool abort_compute(void * data) {
    auto * state = static_cast<State *>(data);
    return state->cancelled.load() || Clock::now() > state->deadline;
}
static bool load_progress(float, void * data) {
    return !static_cast<State *>(data)->cancelled.load();
}
// Do not print model metadata, prompt, tokens or response to public Android logs.
static void discard_log(ggml_log_level, const char *, void *) {}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_minifilm_benchmark_BenchmarkActivity_nativeCreate(JNIEnv *, jclass) {
    std::call_once(backend_once, [] { llama_log_set(discard_log, nullptr); llama_backend_init(); });
    auto state = std::make_shared<State>();
    const jlong id = next_id.fetch_add(1);
    std::lock_guard<std::mutex> guard(registry_mutex);
    registry.emplace(id, std::move(state));
    return id;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_benchmark_BenchmarkActivity_nativeBegin(JNIEnv *, jclass, jlong id) {
    auto state = get(id);
    if (state) state->cancelled.store(false);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_benchmark_BenchmarkActivity_nativeLoad(JNIEnv * env, jclass, jlong id, jstring path) {
    auto state = get(id);
    if (!state) { fail(env, "Benchmark closed."); return; }
    if (state->context) return;
    const auto features = getauxval(AT_HWCAP);
    if (!(features & HWCAP_ASIMDDP) || !(features & HWCAP_ASIMDHP)) {
        fail(env, "This build needs ARM dot-product and FP16 CPU support."); return;
    }
    if (state->cancelled.load()) { fail(env, "Stopped before loading."); return; }
    const auto started = Clock::now();
    const char * filename = env->GetStringUTFChars(path, nullptr);
    if (!filename) return;
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;
    mp.progress_callback = load_progress;
    mp.progress_callback_user_data = state.get();
    state->model = llama_model_load_from_file(filename, mp);
    env->ReleaseStringUTFChars(path, filename);
    if (!state->model) { fail(env, state->cancelled.load() ? "Stopped during loading." : "GGUF could not load."); return; }
    auto cp = llama_context_default_params();
    cp.n_ctx = 1024; cp.n_batch = 128; cp.n_ubatch = 128;
    cp.n_threads = 4; cp.n_threads_batch = 4;
    cp.no_perf = false;
    cp.abort_callback = abort_compute;
    cp.abort_callback_data = state.get();
    state->deadline = Clock::now() + std::chrono::minutes(3);
    state->context = llama_init_from_model(state->model, cp);
    state->metrics[0] = elapsed(started, Clock::now());
    if (!state->context) {
        llama_model_free(state->model); state->model = nullptr;
        fail(env, "Context could not initialize.");
    }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_minifilm_benchmark_BenchmarkActivity_nativeRun(JNIEnv * env, jclass, jlong id, jbyteArray input) {
    auto state = get(id);
    if (!state || !state->context) { fail(env, "Local model is not ready."); return nullptr; }
    const double load_ms = state->metrics[0];
    std::fill(std::begin(state->metrics), std::end(state->metrics), 0.0);
    state->metrics[0] = load_ms; state->metrics[9] = state->ran ? 1 : 0;
    state->metrics[11] = 1024; state->metrics[12] = 4;
    state->deadline = Clock::now() + std::chrono::minutes(3);
    const auto started = Clock::now();
    std::string user(env->GetArrayLength(input), '\0');
    env->GetByteArrayRegion(input, 0, user.size(), reinterpret_cast<jbyte *>(user.data()));
    if (env->ExceptionCheck()) return nullptr;
    const char * metadata_template = llama_model_chat_template(state->model, nullptr);
    if (!metadata_template) { fail(env, "GGUF has no supported chat template."); return nullptr; }
    const char * system = "Give one short filming cue in English, under 12 words. No reasoning. No aircraft commands.";
    llama_chat_message messages[] = {{"system", system}, {"user", user.c_str()}};
    int needed = llama_chat_apply_template(metadata_template, messages, 2, true, nullptr, 0);
    if (needed <= 0 || needed > 16000) { fail(env, "GGUF chat template is unsupported."); return nullptr; }
    std::vector<char> formatted(needed + 1);
    int actual = llama_chat_apply_template(metadata_template, messages, 2, true, formatted.data(), formatted.size());
    if (actual != needed) { fail(env, "Chat template formatting failed."); return nullptr; }
    std::string prompt(formatted.data(), needed);
    // This pinned llama.cpp API recognizes Qwen's metadata as ChatML. Its simple
    // renderer cannot set Jinja enable_thinking=false. Use the Qwen closed-think
    // prefix only when the metadata actually declares the Qwen think markers.
    const std::string tmpl(metadata_template);
    if (tmpl.find("<think>") != std::string::npos && tmpl.find("<|im_start|>") != std::string::npos)
        prompt += "<think>\n\n</think>\n\n";
    const auto * vocab = llama_model_get_vocab(state->model);
    int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
    if (count <= 0 || count > 900) { fail(env, "Shorten the prompt; maximum 900 input tokens."); return nullptr; }
    std::vector<llama_token> tokens(count);
    if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) != count) {
        fail(env, "Prompt tokenization failed."); return nullptr;
    }
    llama_memory_clear(llama_get_memory(state->context), true);
    state->metrics[1] = elapsed(started, Clock::now()); state->metrics[6] = count;
    auto * sampler = llama_sampler_init_greedy();
    const auto prefill_start = Clock::now();
    bool stopped = abort_compute(state.get());
    for (int offset = 0; offset < count && !stopped; offset += 128) {
        const int n = std::min(128, count - offset);
        int status = llama_decode(state->context, llama_batch_get_one(tokens.data() + offset, n));
        if (status != 0) {
            if (!abort_compute(state.get())) { llama_sampler_free(sampler); fail(env, "CPU prefill failed."); return nullptr; }
            stopped = true;
        }
    }
    state->metrics[2] = elapsed(prefill_start, Clock::now());
    std::string output;
    Clock::time_point first, last;
    int generated = 0, decoded = 0;
    bool ended = false;
    for (int i = 0; i < 32 && !stopped; ++i) {
        if (abort_compute(state.get())) { stopped = true; break; }
        auto token = llama_sampler_sample(sampler, state->context, -1);
        if (llama_vocab_is_eog(vocab, token)) { ended = true; break; }
        std::vector<char> piece(256);
        int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (length < 0) { piece.resize(-length); length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
        if (length > 0) output.append(piece.data(), length);
        last = Clock::now();
        if (++generated == 1) { first = last; state->metrics[3] = elapsed(started, first); }
        // No unnecessary final decode after the fixed 32-token limit.
        if (i == 31) break;
        const int status = llama_decode(state->context, llama_batch_get_one(&token, 1));
        if (status != 0) {
            if (!abort_compute(state.get())) { llama_sampler_free(sampler); fail(env, "CPU decode failed."); return nullptr; }
            stopped = true;
        } else ++decoded;
    }
    llama_sampler_free(sampler);
    state->metrics[4] = generated > 1 ? elapsed(first, last) : 0;
    state->metrics[5] = elapsed(started, Clock::now());
    state->metrics[7] = generated; state->metrics[8] = decoded;
    state->metrics[10] = (stopped || state->cancelled.load()) ? 1 : 0;
    state->metrics[13] = state->metrics[10] == 1 ? 3 : (ended ? 1 : 2);
    state->ran = true;
    // EOG is not counted as an output token. Completion is not a quality judgment.
    auto result = env->NewByteArray(output.size());
    if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
    return result;
}
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_minifilm_benchmark_BenchmarkActivity_nativeMetrics(JNIEnv * env, jclass, jlong id) {
    auto state = get(id);
    if (!state) return nullptr;
    auto result = env->NewDoubleArray(14);
    if (result) env->SetDoubleArrayRegion(result, 0, 14, state->metrics);
    return result;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_benchmark_BenchmarkActivity_nativeCancel(JNIEnv *, jclass, jlong id) {
    auto state = get(id); if (state) state->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_benchmark_BenchmarkActivity_nativeClose(JNIEnv *, jclass, jlong id) {
    std::lock_guard<std::mutex> guard(registry_mutex); registry.erase(id);
}
