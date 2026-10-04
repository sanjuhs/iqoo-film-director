#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include <sys/auxv.h>
#include <asm/hwcap.h>

namespace {
constexpr int CONTEXT_SIZE = 1536;
constexpr int OUTPUT_TOKENS = 192;
struct ReferenceState {
    llama_model * model = nullptr;
    llama_context * text = nullptr;
    mtmd_context * vision = nullptr;
    std::atomic<bool> cancelled{false};
    std::chrono::steady_clock::time_point deadline = std::chrono::steady_clock::now() + std::chrono::seconds(180);
    ~ReferenceState() {
        if (vision) mtmd_free(vision);
        if (text) llama_free(text);
        if (model) llama_model_free(model);
    }
};
std::once_flag reference_backend;
std::mutex reference_jobs;
void phase(const char * message) { __android_log_print(ANDROID_LOG_INFO, "MiniFilmReferenceNative", "%s", message); }
void fail(JNIEnv * env, const char * message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
bool aborted(void * data) {
    auto * state = static_cast<ReferenceState *>(data);
    return state->cancelled.load() || std::chrono::steady_clock::now() > state->deadline;
}
void check(ReferenceState * state) { if (aborted(state)) throw std::runtime_error("Frame review cancelled or timed out."); }
bool load_progress(float, void * data) { return !aborted(data); }
bool vision_progress(ggml_tensor *, bool ask, void * data) {
    // Ask to observe graph nodes; false on the observation call cancels computation.
    return ask || !aborted(data);
}
std::string path_string(JNIEnv * env, jstring value) {
    if (!value) throw std::runtime_error("Missing local vision file.");
    const char * text = env->GetStringUTFChars(value, nullptr);
    if (!text) throw std::runtime_error("Unable to read local vision path.");
    std::string result(text);
    env->ReleaseStringUTFChars(value, text);
    return result;
}
const char * grammar = R"GBNF(root ::= "{" ws "\"subject\"" ws ":" ws text "," ws "\"uncertainty\"" ws ":" ws text "}" ws
text ::= "\"" [a-zA-Z0-9 .,;:!?()'-]{1,80} "\""
ws ::= [ \t\n\r]*
)GBNF";
std::string observe(ReferenceState * state, const std::string & core, const std::string & projector,
                    const std::vector<unsigned char> & rgb, int width, int height) {
    std::lock_guard<std::mutex> serial(reference_jobs);
    check(state);
    std::call_once(reference_backend, [] { llama_backend_init(); });
    phase("core_load_started backend=cpu");
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;
    mp.progress_callback = load_progress;
    mp.progress_callback_user_data = state;
    state->model = llama_model_load_from_file(core.c_str(), mp);
    if (!state->model) throw std::runtime_error("Local reference core could not load.");
    check(state);
    auto cp = llama_context_default_params();
    cp.n_ctx = CONTEXT_SIZE;
    cp.n_batch = 512;
    cp.n_ubatch = 128;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;
    cp.abort_callback = aborted;
    cp.abort_callback_data = state;
    state->text = llama_init_from_model(state->model, cp);
    if (!state->text) throw std::runtime_error("Local reference context could not initialize.");
    check(state);
    phase("projector_load_started backend=cpu");
    auto vp = mtmd_context_params_default();
    vp.use_gpu = false;
    vp.n_threads = 4;
    vp.warmup = false;
    vp.image_min_tokens = 64;
    vp.image_max_tokens = 256;
    vp.batch_max_tokens = 256;
    vp.progress_callback = load_progress;
    vp.progress_callback_user_data = state;
    vp.cb_eval = vision_progress;
    vp.cb_eval_user_data = state;
    state->vision = mtmd_init_from_file(projector.c_str(), state->model, vp);
    if (!state->vision || !mtmd_support_vision(state->vision))
        throw std::runtime_error("Matching local vision projector could not load.");
    check(state);
    phase("projector_ready backend=cpu threads=4 image_tokens_max=256");
    std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)> bitmap(
            mtmd_bitmap_init(width, height, rgb.data()), mtmd_bitmap_free);
    std::unique_ptr<mtmd_input_chunks, decltype(&mtmd_input_chunks_free)> chunks(
            mtmd_input_chunks_init(), mtmd_input_chunks_free);
    if (!bitmap || !chunks) throw std::runtime_error("Local frame input could not initialize.");
    std::string before = "<|im_start|>system\nObserve only this single frame. Return a JSON object with exactly two keys: subject and uncertainty. "
            "Subject and uncertainty must each be concise 2-6 word PHRASES, never sentences or narratives. "
            "Subject names the clearly visible subject. Uncertainty names missing or unclear information. "
            "For an empty or black frame use subject no identifiable subject. "
            "Do not invent people, objects, action, story, brands, location or purpose. Movement and audio are unknown from one frame. "
            "Keep each phrase well below 80 characters. No advice, explanation, trailing conjunction or unfinished phrase.\n<|im_end|>\n<|im_start|>user\n";
    std::string after = "\nDescribe this frame.\n<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n";
    mtmd_input_text beginning{before.data(), before.size(), false, true};
    mtmd_input_text ending{after.data(), after.size(), false, true};
    mtmd_input_part p0{&beginning, nullptr}, p1{nullptr, bitmap.get()}, p2{&ending, nullptr};
    const mtmd_input_part * parts[] = {&p0, &p1, &p2};
    if (mtmd_tokenize_from_parts(state->vision, chunks.get(), parts, 3, true) != 0)
        throw std::runtime_error("Local frame preprocessing failed.");
    const size_t input_tokens = mtmd_helper_get_n_tokens(chunks.get());
    const llama_pos input_positions = mtmd_helper_get_n_pos(chunks.get());
    if (input_tokens == 0 || input_tokens > 1000 || input_positions <= 0
            || input_tokens + OUTPUT_TOKENS >= llama_n_ctx(state->text)
            || input_positions + OUTPUT_TOKENS >= static_cast<llama_pos>(llama_n_ctx(state->text)))
        throw std::runtime_error("Frame input exceeds bounded local context.");
    __android_log_print(ANDROID_LOG_INFO, "MiniFilmReferenceNative", "frame_encode_started input_tokens=%zu positions=%d", input_tokens, input_positions);
    check(state);
    llama_pos next_position = 0;
    if (mtmd_helper_eval_chunks(state->vision, state->text, chunks.get(), 0, 0, 128, true, &next_position) != 0)
        throw std::runtime_error("Local frame encoding stopped.");
    check(state);
    phase("frame_encode_complete");
    const auto * vocab = llama_model_get_vocab(state->model);
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    if (!sampler) throw std::runtime_error("Local observation sampler could not initialize.");
    auto * schema = llama_sampler_init_grammar(vocab, grammar, "root");
    if (!schema) throw std::runtime_error("Local observation schema could not initialize.");
    llama_sampler_chain_add(sampler.get(), schema);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(.9f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(.1f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(42));
    std::string output;
    bool ended = false;
    for (int i = 0; i < OUTPUT_TOKENS; i++) {
        check(state);
        llama_token token = llama_sampler_sample(sampler.get(), state->text, -1);
        if (llama_vocab_is_eog(vocab, token)) { ended = true; break; }
        std::vector<char> piece(256);
        int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (length < 0) { piece.resize(-length); length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
        if (length > 0) output.append(piece.data(), length);
        if (output.size() > 512) throw std::runtime_error("Local observation exceeds its text bound.");
        // Explicit 1-D M-RoPE coordinates continue from the helper's image/text positions.
        llama_pos positions[] = {next_position, next_position, next_position, next_position};
        int32_t nseq = 1;
        llama_seq_id seq = 0;
        llama_seq_id * seqs[] = {&seq};
        int8_t logits = 1;
        llama_batch batch{1, &token, nullptr, positions, &nseq, seqs, &logits};
        if (llama_decode(state->text, batch) != 0) throw std::runtime_error("Local observation generation stopped.");
        next_position++;
    }
    if (!ended || output.empty()) throw std::runtime_error("Local observation did not finish within its token bound.");
    phase("observation_complete backend=cpu");
    return output;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_minifilm_director_VisionReference_nativeCreate(JNIEnv * env, jclass) {
#ifdef DIRECTOR_REQUIRE_DOTPROD_FP16
    const unsigned long features = getauxval(AT_HWCAP);
    if ((features & HWCAP_ASIMDDP) == 0 || (features & HWCAP_ASIMDHP) == 0) {
        fail(env, "This vision build requires CPU dot-product and FP16 support."); return 0;
    }
#endif
    try { return reinterpret_cast<jlong>(new ReferenceState()); }
    catch (const std::exception &) { fail(env, "Local vision state could not initialize."); return 0; }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_minifilm_director_VisionReference_nativeObserve(JNIEnv * env, jclass, jlong handle,
                                                       jstring core, jstring projector, jbyteArray input,
                                                       jint width, jint height) {
    auto * state = reinterpret_cast<ReferenceState *>(handle);
    if (!state || !input || width < 1 || height < 1 || width > 512 || height > 512
            || env->GetArrayLength(input) != width * height * 3) {
        fail(env, "Invalid local frame input."); return nullptr;
    }
    try {
        std::vector<unsigned char> rgb(width * height * 3);
        env->GetByteArrayRegion(input, 0, rgb.size(), reinterpret_cast<jbyte *>(rgb.data()));
        if (env->ExceptionCheck()) return nullptr;
        std::string output = observe(state, path_string(env, core), path_string(env, projector), rgb, width, height);
        auto result = env->NewByteArray(output.size());
        if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception & failure) {
        __android_log_print(ANDROID_LOG_WARN, "MiniFilmReferenceNative", "frame_failed reason=%s", failure.what());
        fail(env, failure.what()); return nullptr;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_director_VisionReference_nativeCancel(JNIEnv *, jclass, jlong handle) {
    if (handle) reinterpret_cast<ReferenceState *>(handle)->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_director_VisionReference_nativeFree(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<ReferenceState *>(handle);
}
