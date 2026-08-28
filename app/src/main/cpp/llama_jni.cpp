#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <random>
#include <sstream>

#include "llama.h"
#include "ggml-backend.h"

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Stan globalny modelu
// ---------------------------------------------------------------------------
static struct llama_model   * g_model   = nullptr;
static struct llama_context * g_ctx     = nullptr;
static bool                   g_loaded  = false;

// ---------------------------------------------------------------------------
// Pomocnicze: tokenizacja
// ---------------------------------------------------------------------------
static std::vector<llama_token> tokenize(const std::string & text, bool add_bos) {
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int n = llama_tokenize(vocab, text.c_str(), (int32_t)text.size(),
                           nullptr, 0, add_bos, true);
    if (n < 0) n = -n;
    std::vector<llama_token> tokens(n);
    llama_tokenize(vocab, text.c_str(), (int32_t)text.size(),
                   tokens.data(), (int32_t)tokens.size(), add_bos, true);
    return tokens;
}

// ---------------------------------------------------------------------------
// Pomocnicze: token -> string
// ---------------------------------------------------------------------------
static std::string token_to_str(llama_token token) {
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n < 0) return "";
    return std::string(buf, n);
}

// ---------------------------------------------------------------------------
// Ręczny sampler (greedy + temperature + top-k + top-p + repeat penalty)
// ---------------------------------------------------------------------------
static llama_token sample_token(
        struct llama_context * ctx,
        const std::vector<llama_token> & last_tokens,
        float temperature, float top_p, int top_k, float repeat_penalty)
{
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int n_vocab = llama_vocab_n_tokens(vocab);

    float * logits = llama_get_logits(ctx);
    std::vector<std::pair<float,int>> candidates(n_vocab);
    for (int i = 0; i < n_vocab; i++) candidates[i] = {logits[i], i};

    // Repeat penalty
    if (repeat_penalty != 1.0f && !last_tokens.empty()) {
        for (auto tok : last_tokens) {
            if (tok >= 0 && tok < n_vocab) {
                auto & lp = candidates[tok].first;
                lp = lp > 0 ? lp / repeat_penalty : lp * repeat_penalty;
            }
        }
    }

    // Greedy
    if (temperature <= 0.0f) {
        return (llama_token)std::max_element(candidates.begin(), candidates.end())->second;
    }

    // Temperature
    for (auto & c : candidates) c.first /= temperature;

    // Softmax
    float max_l = std::max_element(candidates.begin(), candidates.end())->first;
    float sum = 0.0f;
    for (auto & c : candidates) { c.first = std::exp(c.first - max_l); sum += c.first; }
    for (auto & c : candidates) c.first /= sum;

    // Top-K
    if (top_k > 0 && top_k < n_vocab) {
        std::partial_sort(candidates.begin(), candidates.begin() + top_k,
                          candidates.end(), [](auto & a, auto & b){ return a.first > b.first; });
        candidates.resize(top_k);
        float s2 = 0; for (auto & c : candidates) s2 += c.first;
        for (auto & c : candidates) c.first /= s2;
    }

    // Top-P
    if (top_p < 1.0f) {
        std::sort(candidates.begin(), candidates.end(), [](auto & a, auto & b){ return a.first > b.first; });
        float cumsum = 0;
        size_t last = candidates.size();
        for (size_t i = 0; i < candidates.size(); i++) {
            cumsum += candidates[i].first;
            if (cumsum >= top_p) { last = i + 1; break; }
        }
        candidates.resize(last);
        float s2 = 0; for (auto & c : candidates) s2 += c.first;
        for (auto & c : candidates) c.first /= s2;
    }

    // Losuj
    std::random_device rd;
    std::mt19937 gen(rd());
    std::vector<float> probs(candidates.size());
    for (size_t i = 0; i < candidates.size(); i++) probs[i] = candidates[i].first;
    std::discrete_distribution<int> dist(probs.begin(), probs.end());
    return (llama_token)candidates[dist(gen)].second;
}

// ---------------------------------------------------------------------------
// JNI: ładowanie backendów GGML z katalogu natywnych bibliotek aplikacji
// Musi być wywołane RAZ przy starcie aplikacji, zanim nativeInit().
// ---------------------------------------------------------------------------
static bool g_backends_loaded = false;

extern "C" JNIEXPORT void JNICALL
Java_com_llamaagent_LlamaEngine_nativeInitBackends(
        JNIEnv * env, jclass /*clazz*/,
        jstring nativeLibDir)
{
    if (g_backends_loaded) return;

    const char * dir = env->GetStringUTFChars(nativeLibDir, nullptr);
    LOGI("Ładowanie backendów GGML z: %s", dir);
    ggml_backend_load_all_from_path(dir);
    env->ReleaseStringUTFChars(nativeLibDir, dir);

    int n_backends = (int)ggml_backend_reg_count();
    int n_devices  = (int)ggml_backend_dev_count();
    LOGI("Załadowano backendów: %d, urządzeń: %d", n_backends, n_devices);

    g_backends_loaded = true;
}

// ---------------------------------------------------------------------------
// JNI: inicjalizacja modelu
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_llamaagent_LlamaEngine_nativeInit(
        JNIEnv * env, jobject /*thiz*/,
        jstring modelPath, jint nThreads, jint nCtx)
{
    // Zwolnij poprzedni model jeśli był
    if (g_ctx)   { llama_free(g_ctx);        g_ctx   = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_loaded = false;

    // Inicjalizuj backend (jeśli backendów jeszcze nie załadowano ręcznie,
    // llama_backend_init i tak spróbuje – wymagamy jednak jawnego ładowania)
    llama_backend_init();

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Ładowanie modelu: %s", path);

    struct llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only na Androidzie

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Nie udało się załadować modelu!");
        return JNI_FALSE;
    }

    struct llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = (uint32_t)nCtx;
    cparams.n_threads = (uint32_t)nThreads;
    cparams.n_threads_batch = (uint32_t)nThreads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Nie udało się utworzyć kontekstu!");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_loaded = true;
    LOGI("Model załadowany pomyślnie. n_ctx=%d, n_threads=%d", nCtx, nThreads);
    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// JNI: generacja (blokująca)
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jstring JNICALL
Java_com_llamaagent_LlamaEngine_nativeGenerate(
        JNIEnv * env, jobject /*thiz*/,
        jstring prompt, jint maxTokens,
        jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty)
{
    if (!g_loaded) {
        return env->NewStringUTF("[BŁĄD: Model nie jest załadowany]");
    }

    const char * prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    // Tokenizacja
    std::vector<llama_token> tokens = tokenize(prompt_str, true);
    if (tokens.empty()) {
        return env->NewStringUTF("");
    }

    llama_memory_clear(llama_get_memory(g_ctx), false);

    // Przetwórz prompt
    struct llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        return env->NewStringUTF("[BŁĄD: Nie można zdekodować promptu]");
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    llama_token eos = llama_vocab_eos(vocab);
    llama_token eot = llama_vocab_eot(vocab);

    std::string result;
    std::vector<llama_token> last_tokens(tokens.begin(), tokens.end());

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = sample_token(g_ctx, last_tokens,
                                       temperature, topP, topK, repeatPenalty);

        if (tok == eos || tok == eot) break;

        result += token_to_str(tok);
        last_tokens.push_back(tok);
        if (last_tokens.size() > 64) last_tokens.erase(last_tokens.begin());

        // Następny krok
        struct llama_batch next = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, next) != 0) break;
    }

    return env->NewStringUTF(result.c_str());
}

// ---------------------------------------------------------------------------
// Callback interface dla streamingu
// ---------------------------------------------------------------------------
static jclass    g_callback_class  = nullptr;
static jmethodID g_on_token_method = nullptr;
static jmethodID g_on_complete_method = nullptr;
static jmethodID g_on_error_method = nullptr;

// ---------------------------------------------------------------------------
// JNI: generacja strumieniowa
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_llamaagent_LlamaEngine_nativeGenerateStream(
        JNIEnv * env, jobject /*thiz*/,
        jstring prompt, jint maxTokens,
        jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty,
        jobject callback)
{
    if (!g_loaded) {
        jclass cb_cls = env->GetObjectClass(callback);
        jmethodID err = env->GetMethodID(cb_cls, "onError", "(Ljava/lang/String;)V");
        jstring msg = env->NewStringUTF("Model nie jest załadowany");
        env->CallVoidMethod(callback, err, msg);
        env->DeleteLocalRef(msg);
        return;
    }

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token    = env->GetMethodID(cb_cls, "onToken",    "(Ljava/lang/String;)V");
    jmethodID on_complete = env->GetMethodID(cb_cls, "onComplete", "()V");
    jmethodID on_error    = env->GetMethodID(cb_cls, "onError",    "(Ljava/lang/String;)V");

    const char * prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    std::vector<llama_token> tokens = tokenize(prompt_str, true);
    if (tokens.empty()) {
        env->CallVoidMethod(callback, on_complete);
        return;
    }

    llama_memory_clear(llama_get_memory(g_ctx), false);

    struct llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        jstring msg = env->NewStringUTF("Błąd dekodowania promptu");
        env->CallVoidMethod(callback, on_error, msg);
        env->DeleteLocalRef(msg);
        return;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    llama_token eos = llama_vocab_eos(vocab);
    llama_token eot = llama_vocab_eot(vocab);

    std::vector<llama_token> last_tokens(tokens.begin(), tokens.end());

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = sample_token(g_ctx, last_tokens,
                                       temperature, topP, topK, repeatPenalty);

        if (tok == eos || tok == eot) break;

        std::string piece = token_to_str(tok);
        jstring jpiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, on_token, jpiece);
        env->DeleteLocalRef(jpiece);

        last_tokens.push_back(tok);
        if (last_tokens.size() > 64) last_tokens.erase(last_tokens.begin());

        struct llama_batch next = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, next) != 0) break;
    }

    env->CallVoidMethod(callback, on_complete);
}

// ---------------------------------------------------------------------------
// JNI: zwolnienie zasobów
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_llamaagent_LlamaEngine_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/)
{
    if (g_ctx)   { llama_free(g_ctx);        g_ctx   = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
    g_loaded = false;
    LOGI("Model zwolniony.");
}

// ---------------------------------------------------------------------------
// JNI: rozmiar kontekstu
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jint JNICALL
Java_com_llamaagent_LlamaEngine_nativeGetContextSize(JNIEnv * /*env*/, jobject /*thiz*/)
{
    if (!g_ctx) return 0;
    return (jint)llama_n_ctx(g_ctx);
}

// ---------------------------------------------------------------------------
// JNI: czy model załadowany
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_llamaagent_LlamaEngine_nativeIsLoaded(JNIEnv * /*env*/, jobject /*thiz*/)
{
    return g_loaded ? JNI_TRUE : JNI_FALSE;
}
