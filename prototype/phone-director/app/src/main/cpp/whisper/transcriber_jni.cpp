#include <jni.h>
#include <android/log.h>
#include <whisper.h>
#include <atomic>
#include <cmath>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

namespace {
std::atomic<bool> cancelled{false};
std::mutex inferenceMutex;
bool shouldAbort(void *) { return cancelled.load(); }

std::string quote(const char *text) {
    std::string result = "\"";
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(text); *p; ++p) {
        if (*p == '"' || *p == '\\') { result += '\\'; result += static_cast<char>(*p); }
        else if (*p == '\n') result += "\\n";
        else if (*p == '\r') result += "\\r";
        else if (*p == '\t') result += "\\t";
        else if (*p >= 32) result += static_cast<char>(*p);
    }
    result += '"'; return result;
}
jstring error(JNIEnv *env, const char *message) {
    std::string result = "{\"error\":" + quote(message) + "}";
    return env->NewStringUTF(result.c_str());
}
void modelLog(ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, "MiniFilmASR", "%s", text);
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_minifilm_director_ClipTranscriber_nativeTranscribe(JNIEnv *env, jclass,
        jstring modelPath, jfloatArray input) {
    std::lock_guard<std::mutex> lock(inferenceMutex);
    cancelled.store(false);
    if (!modelPath || !input) return error(env, "Missing audio or model path.");
    const jsize count = env->GetArrayLength(input);
    if (count < 1600 || count > 16000 * 180) return error(env, "Audio length outside supported bounds.");
    std::vector<float> samples(count);
    env->GetFloatArrayRegion(input, 0, count, samples.data());
    if (env->ExceptionCheck()) return nullptr;
    double energy = 0;
    for (float sample : samples) {
        if (!std::isfinite(sample)) return error(env, "Invalid decoded PCM.");
        energy += sample * sample;
    }
    if (energy / count < 0.0000001) return env->NewStringUTF("{\"segments\":[]}");
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return nullptr;
    whisper_log_set(modelLog, nullptr);
    whisper_context_params contextParams = whisper_context_default_params();
    contextParams.use_gpu = false; contextParams.flash_attn = false;
    whisper_context *context = whisper_init_from_file_with_params(path, contextParams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!context) return error(env, "Could not load the local tiny.en model.");
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 4;
    params.language = "en";
    params.translate = false; params.no_context = true;
    params.print_realtime = false; params.print_progress = false;
    params.print_timestamps = false; params.print_special = false;
    params.token_timestamps = true; params.max_len = 42; params.split_on_word = true;
    params.suppress_nst = true;
    params.abort_callback = shouldAbort; params.abort_callback_user_data = nullptr;
    int status = whisper_full(context, params, samples.data(), count);
    if (status != 0 || cancelled.load()) {
        whisper_free(context); return error(env, cancelled.load() ? "Transcription cancelled." : "Local speech inference failed.");
    }
    std::ostringstream json; json << "{\"segments\":[";
    bool first = true;
    const int segments = whisper_full_n_segments(context);
    for (int i = 0; i < segments; ++i) {
        const char *text = whisper_full_get_segment_text(context, i);
        if (!text || !*text) continue;
        if (!first) json << ',';
        first = false;
        json << "{\"startMs\":" << whisper_full_get_segment_t0(context, i) * 10
             << ",\"endMs\":" << whisper_full_get_segment_t1(context, i) * 10
             << ",\"text\":" << quote(text) << '}';
    }
    json << "]}";
    whisper_free(context);
    __android_log_print(ANDROID_LOG_INFO, "MiniFilmASR", "Native inference CPU threads=4 model=tiny.en segments=%d", segments);
    return env->NewStringUTF(json.str().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_minifilm_director_ClipTranscriber_nativeCancel(JNIEnv *, jclass) { cancelled.store(true); }
