#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include <atomic>
#include <algorithm>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <sys/auxv.h>
#include <asm/hwcap.h>

struct DirectorModel {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::atomic<bool> cancelled{false};
    std::chrono::steady_clock::time_point deadline;
    ~DirectorModel() {
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};
static std::once_flag backend_once;
static void phase(const char * message) { __android_log_print(ANDROID_LOG_INFO, "MiniFilmNativeAI", "%s", message); }
static bool abort_generation(void * data) {
    auto * state = static_cast<DirectorModel *>(data);
    return state->cancelled.load() || std::chrono::steady_clock::now() > state->deadline;
}
static void fail(JNIEnv * env, const char * message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_minifilm_director_LocalPlanner_nativeLoad(JNIEnv * env, jclass, jstring path) {
#ifdef DIRECTOR_REQUIRE_DOTPROD_FP16
    const unsigned long features = getauxval(AT_HWCAP);
    if ((features & HWCAP_ASIMDDP) == 0 || (features & HWCAP_ASIMDHP) == 0) {
        fail(env, "This model build requires CPU dot-product and FP16 support. Use the editable template.");
        return 0;
    }
#endif
    std::call_once(backend_once, [] { llama_backend_init(); });
    phase("model_load_started backend=cpu");
    const char * filename = env->GetStringUTFChars(path, nullptr);
    if (!filename) return 0;
    auto state = std::make_unique<DirectorModel>();
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;
    state->model = llama_model_load_from_file(filename, mp);
    env->ReleaseStringUTFChars(path, filename);
    if (!state->model) { fail(env, "Local model could not load. Use the editable template."); return 0; }
    phase("model_loaded backend=cpu");
    auto cp = llama_context_default_params();
    cp.n_ctx = 1536;
    cp.n_batch = 512;
    cp.n_ubatch = 128;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;
    cp.no_perf = false;
    cp.abort_callback = abort_generation;
    cp.abort_callback_data = state.get();
    state->deadline = std::chrono::steady_clock::now() + std::chrono::minutes(2);
    state->context = llama_init_from_model(state->model, cp);
    if (!state->context) { fail(env, "Local model context could not initialize. Use the editable template."); return 0; }
    phase("context_ready threads=4 context=1536");
    return reinterpret_cast<jlong>(state.release());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_minifilm_director_LocalPlanner_nativeGenerate(JNIEnv * env, jclass, jlong handle, jbyteArray input, jint max_tokens) {
    auto * state = reinterpret_cast<DirectorModel *>(handle);
    if (!state || !state->context) { fail(env, "Local model is unavailable."); return nullptr; }
    state->cancelled.store(false);
    state->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(100);
    std::string prompt(env->GetArrayLength(input), '\0');
    env->GetByteArrayRegion(input, 0, prompt.size(), reinterpret_cast<jbyte *>(prompt.data()));
    const auto * vocab = llama_model_get_vocab(state->model);
    int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
    if (count <= 0 || count > 900) { fail(env, "Brief is too long for the local planner."); return nullptr; }
    std::vector<llama_token> tokens(count);
    if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) < 0) {
        fail(env, "Local prompt tokenization failed."); return nullptr;
    }
    llama_memory_clear(llama_get_memory(state->context), true);
    __android_log_print(ANDROID_LOG_INFO, "MiniFilmNativeAI", "prompt_started tokens=%d", count);
    for (int offset = 0; offset < count; offset += 128) {
        int n = std::min(128, count - offset);
        if (llama_decode(state->context, llama_batch_get_one(tokens.data() + offset, n)) != 0) {
            fail(env, "Local planning stopped while reading the brief."); return nullptr;
        }
    }
    phase("prompt_complete");
    auto sp = llama_sampler_chain_default_params();
    auto * sampler = llama_sampler_chain_init(sp);
    // Grammar constrains syntax and duration only; creative quality still needs review.
    const char * grammar = R"GRAMMAR(
root ::= "[" ws shot "," ws shot "," ws shot "," ws shot "," ws shot "]" ws
shot ::= "{" ws "\"title\"" ws ":" ws title "," ws "\"instruction\"" ws ":" ws instruction "," ws "\"caption\"" ws ":" ws short "," ws "\"duration_ms\"" ws ":" ws duration "}" ws
title ::= "\"" char{1,24} "\"" ws
short ::= "\"" char{1,30} "\"" ws
instruction ::= "\"" char{1,90} "\"" ws
char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4})
duration ::= ("3000" | "4000" | "5000" | "6000" | "7000" | "8000") ws
ws ::= [ \t\n\r]*
)GRAMMAR";
    auto * constrained = llama_sampler_init_grammar(vocab, grammar, "root");
    if (!constrained) { llama_sampler_free(sampler); fail(env, "Local shot schema could not initialize."); return nullptr; }
    llama_sampler_chain_add(sampler, constrained);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(.65f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));
    std::string output;
    bool aborted = false;
    for (int i = 0; i < std::min(580, max_tokens); ++i) {
        if (abort_generation(state)) { aborted = true; break; }
        auto token = llama_sampler_sample(sampler, state->context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        if (i == 0 || (i + 1) % 100 == 0)
            __android_log_print(ANDROID_LOG_INFO, "MiniFilmNativeAI", "generation_progress tokens=%d", i + 1);
        std::vector<char> buffer(256);
        int len = llama_token_to_piece(vocab, token, buffer.data(), buffer.size(), 0, false);
        if (len < 0) { buffer.resize(-len); len = llama_token_to_piece(vocab, token, buffer.data(), buffer.size(), 0, false); }
        if (len > 0) output.append(buffer.data(), len);
        if (llama_decode(state->context, llama_batch_get_one(&token, 1)) != 0) { aborted = true; break; }
    }
    llama_sampler_free(sampler);
    __android_log_print(ANDROID_LOG_INFO, "MiniFilmNativeAI", "generation_finished aborted=%d output_bytes=%zu", aborted, output.size());
    if (aborted) { fail(env, "Local planning timed out or was cancelled. Use the editable template."); return nullptr; }
    auto result = env->NewByteArray(output.size());
    if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_director_LocalPlanner_nativeCancel(JNIEnv *, jclass, jlong handle) {
    auto * state = reinterpret_cast<DirectorModel *>(handle);
    if (state) state->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_director_LocalPlanner_nativeFree(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<DirectorModel *>(handle);
}
