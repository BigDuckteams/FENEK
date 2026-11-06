// JNI bridge between app.fenek.llm.LlamaNative and llama.cpp.
//
// One Engine is one loaded model with its context. A request (generate) applies the model's chat
// template to a system and a user message, decodes the prompt, then samples greedily (optionally
// constrained by a GBNF grammar) until the end of the answer, the token limit, the time limit or a
// cancellation. Text crosses the boundary as UTF-8 byte arrays, not as jstring, because JNI's
// "modified UTF-8" mangles characters outside the basic plane.
//
// Errors are reported as Java exceptions: TimeoutException, CancellationException (both from
// java.util.concurrent) and IllegalStateException for everything else.

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <exception>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

#define TAG "FenekLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

// The longest request is a window of about 1500 prompt tokens plus 600 generated ones.
constexpr int CONTEXT_TOKENS = 4096;
constexpr int BATCH_TOKENS = 512;

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::atomic<bool> cancelled{false};
};

enum class Stop { None, Timeout, Cancelled };

void log_callback(ggml_log_level level, const char *text, void * /*user*/) {
    int priority = ANDROID_LOG_INFO;
    if (level == GGML_LOG_LEVEL_ERROR) priority = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_WARN) priority = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_DEBUG) return;
    __android_log_write(priority, TAG, text);
}

std::string to_string(JNIEnv *env, jbyteArray bytes) {
    if (bytes == nullptr) return {};
    const jsize length = env->GetArrayLength(bytes);
    std::string result(static_cast<size_t>(length), '\0');
    if (length > 0) env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(result.data()));
    return result;
}

jbyteArray to_bytes(JNIEnv *env, const std::string &text) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(text.size()));
    if (array != nullptr && !text.empty()) {
        env->SetByteArrayRegion(array, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    }
    return array;
}

void throw_java(JNIEnv *env, const char *class_name, const char *message) {
    jclass type = env->FindClass(class_name);
    if (type != nullptr) env->ThrowNew(type, message);
}

std::string chatml(const std::string &system, const std::string &user) {
    return "<|im_start|>system\n" + system + "<|im_end|>\n<|im_start|>user\n" + user +
           "<|im_end|>\n<|im_start|>assistant\n";
}

// The prompt as the model was trained to see it; ChatML when the model's template is unknown.
std::string build_prompt(const llama_model *model, const std::string &system, const std::string &user) {
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr) return chatml(system, user);
    const llama_chat_message messages[2] = {{"system", system.c_str()}, {"user", user.c_str()}};
    std::vector<char> buffer(system.size() + user.size() + 1024);
    int32_t written = llama_chat_apply_template(tmpl, messages, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    if (written > static_cast<int32_t>(buffer.size())) {
        buffer.resize(static_cast<size_t>(written));
        written = llama_chat_apply_template(tmpl, messages, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    }
    if (written < 0) return chatml(system, user);
    return std::string(buffer.data(), static_cast<size_t>(written));
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text) {
    int32_t count = -llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, true, true);
    if (count <= 0) return {};
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    count = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), tokens.data(), count, true, true);
    if (count < 0) return {};
    tokens.resize(static_cast<size_t>(count));
    // Some templates write the BOS token themselves and the tokenizer adds another one.
    if (tokens.size() >= 2 && tokens[0] == llama_vocab_bos(vocab) && tokens[1] == tokens[0]) {
        tokens.erase(tokens.begin());
    }
    return tokens;
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_app_fenek_llm_LlamaNative_nativeInit(JNIEnv *env, jobject, jstring native_lib_dir) {
    static bool initialized = false;
    if (initialized) return;
    llama_log_set(log_callback, nullptr);
    const char *path = env->GetStringUTFChars(native_lib_dir, nullptr);
    LOGI("Loading the ggml backends from %s", path);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(native_lib_dir, path);
    llama_backend_init();
    initialized = true;
}

JNIEXPORT jlong JNICALL
Java_app_fenek_llm_LlamaNative_nativeLoad(JNIEnv *env, jobject, jstring model_path, jint threads) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading the model %s with %d threads", path, threads);
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    llama_model *model = nullptr;
    try {
        model = llama_model_load_from_file(path, model_params);
    } catch (const std::exception &e) {
        LOGE("Model load failed: %s", e.what());
    }
    env->ReleaseStringUTFChars(model_path, path);
    if (model == nullptr) return 0;

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(std::min(CONTEXT_TOKENS, llama_model_n_ctx_train(model)));
    context_params.n_batch = BATCH_TOKENS;
    context_params.n_ubatch = BATCH_TOKENS;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.no_perf = true;
    llama_context *ctx = nullptr;
    try {
        ctx = llama_init_from_model(model, context_params);
    } catch (const std::exception &e) {
        LOGE("Context creation failed: %s", e.what());
    }
    if (ctx == nullptr) {
        llama_model_free(model);
        return 0;
    }
    auto *engine = new Engine();
    engine->model = model;
    engine->ctx = ctx;
    engine->vocab = llama_model_get_vocab(model);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_app_fenek_llm_LlamaNative_nativeCancel(JNIEnv *, jobject, jlong handle) {
    if (handle != 0) reinterpret_cast<Engine *>(handle)->cancelled.store(true);
}

JNIEXPORT void JNICALL
Java_app_fenek_llm_LlamaNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return;
    if (engine->ctx != nullptr) llama_free(engine->ctx);
    if (engine->model != nullptr) llama_model_free(engine->model);
    delete engine;
}

JNIEXPORT jbyteArray JNICALL
Java_app_fenek_llm_LlamaNative_nativeGenerate(
        JNIEnv *env, jobject, jlong handle, jbyteArray system_bytes, jbyteArray user_bytes,
        jbyteArray grammar_bytes, jint max_tokens, jlong timeout_ms) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "the model is not loaded");
        return nullptr;
    }
    engine->cancelled.store(false);

    const std::string system = to_string(env, system_bytes);
    const std::string user = to_string(env, user_bytes);
    const std::string grammar = to_string(env, grammar_bytes);
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms);
    auto stop = [&]() {
        if (engine->cancelled.load()) return Stop::Cancelled;
        if (std::chrono::steady_clock::now() > deadline) return Stop::Timeout;
        return Stop::None;
    };

    llama_sampler *sampler = nullptr;
    std::string answer;
    const char *error = nullptr;
    Stop reason = Stop::None;
    size_t prompt_tokens = 0;
    int produced = 0;
    const auto started = std::chrono::steady_clock::now();
    auto prompt_done = started;
    try {
        std::vector<llama_token> prompt = tokenize(engine->vocab, build_prompt(engine->model, system, user));
        prompt_tokens = prompt.size();
        const int context_size = static_cast<int>(llama_n_ctx(engine->ctx));
        if (prompt.empty()) {
            error = "the prompt could not be tokenized";
        } else if (static_cast<int>(prompt.size()) >= context_size - 8) {
            error = "the prompt does not fit into the context";
        }

        if (error == nullptr) {
            const int limit = std::min(static_cast<int>(max_tokens), context_size - static_cast<int>(prompt.size()) - 1);
            llama_memory_clear(llama_get_memory(engine->ctx), true);

            sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
            if (!grammar.empty()) {
                llama_sampler *constraint = llama_sampler_init_grammar(engine->vocab, grammar.c_str(), "root");
                if (constraint == nullptr) {
                    error = "the grammar could not be parsed";
                } else {
                    llama_sampler_chain_add(sampler, constraint);
                }
            }
            llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

            for (size_t from = 0; error == nullptr && reason == Stop::None && from < prompt.size();
                 from += BATCH_TOKENS) {
                const int count = static_cast<int>(std::min<size_t>(BATCH_TOKENS, prompt.size() - from));
                if (llama_decode(engine->ctx, llama_batch_get_one(prompt.data() + from, count)) != 0) {
                    error = "decoding the prompt failed";
                }
                reason = stop();
            }

            prompt_done = std::chrono::steady_clock::now();
            for (; error == nullptr && reason == Stop::None && produced < limit; ++produced) {
                llama_token token = llama_sampler_sample(sampler, engine->ctx, -1);
                if (llama_vocab_is_eog(engine->vocab, token)) break;
                char piece[256];
                const int32_t length = llama_token_to_piece(engine->vocab, token, piece, sizeof(piece), 0, false);
                if (length > 0) answer.append(piece, static_cast<size_t>(length));
                if (llama_decode(engine->ctx, llama_batch_get_one(&token, 1)) != 0) {
                    error = "decoding failed";
                    break;
                }
                reason = stop();
            }
        }
    } catch (const std::exception &e) {
        LOGE("Generation failed: %s", e.what());
        error = "the model failed while generating";
    }

    if (sampler != nullptr) llama_sampler_free(sampler);
    llama_memory_clear(llama_get_memory(engine->ctx), true);

    // Numbers only, never the text: the prompt holds the user's conversation.
    const auto finished = std::chrono::steady_clock::now();
    const auto ms = [](auto from, auto to) {
        return static_cast<long long>(std::chrono::duration_cast<std::chrono::milliseconds>(to - from).count());
    };
    LOGI("generate: prompt %zu tokens in %lld ms, answer %d tokens in %lld ms, %s%s", prompt_tokens,
         ms(started, prompt_done), produced, ms(prompt_done, finished),
         reason == Stop::Timeout ? "timeout" : reason == Stop::Cancelled ? "cancelled" : "finished",
         error != nullptr ? " with an error" : "");

    if (reason == Stop::Cancelled) {
        throw_java(env, "java/util/concurrent/CancellationException", "cancelled");
        return nullptr;
    }
    if (reason == Stop::Timeout) {
        throw_java(env, "java/util/concurrent/TimeoutException", "the request took too long");
        return nullptr;
    }
    if (error != nullptr) {
        throw_java(env, "java/lang/IllegalStateException", error);
        return nullptr;
    }
    return to_bytes(env, answer);
}

} // extern "C"
