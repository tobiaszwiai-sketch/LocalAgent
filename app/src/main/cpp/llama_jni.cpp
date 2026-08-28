// ---------------------------------------------------------------------------
// llama_jni.cpp — most JNI pomiędzy Kotlin (LlamaEngine) a llama.cpp.
//
// Implementacja jest napisana pod API llama.cpp z tagu b3456. Sampling jest
// zaimplementowany ręcznie (temp / top-k / top-p / repeat penalty) na surowych
// logitach, aby uniezależnić kod od zmiennego API samplera w llama.cpp.
// ---------------------------------------------------------------------------
#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>
#include <random>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Stan globalny (pojedyncza instancja silnika)
// ---------------------------------------------------------------------------
static llama_model*   g_model    = nullptr;
static llama_context* g_ctx      = nullptr;
static int            g_n_ctx    = 0;
static int            g_n_threads = 4;
static bool           g_backend_ready = false;
static std::mutex     g_mutex;
static std::mt19937   g_rng(std::random_device{}());

// ---------------------------------------------------------------------------
// Pomocnicze
// ---------------------------------------------------------------------------

// Tokenizacja tekstu na wektor tokenów.
static std::vector<llama_token> tokenize(const std::string& text, bool add_bos) {
    int n_max = (int) text.size() + 16;
    std::vector<llama_token> result(n_max);
    int n = llama_tokenize(g_model, text.c_str(), (int) text.size(),
                           result.data(), (int) result.size(), add_bos, true);
    if (n < 0) {
        result.resize(-n);
        n = llama_tokenize(g_model, text.c_str(), (int) text.size(),
                           result.data(), (int) result.size(), add_bos, true);
    }
    result.resize(std::max(0, n));
    return result;
}

// Zamiana pojedynczego tokenu na fragment tekstu (bajty).
static std::string token_to_piece(llama_token token) {
    char buf[256];
    int n = llama_token_to_piece(g_model, token, buf, sizeof(buf), 0, true);
    if (n < 0) {
        std::string result((size_t)(-n), '\0');
        int n2 = llama_token_to_piece(g_model, token, &result[0], (int) result.size(), 0, true);
        result.resize(std::max(0, n2));
        return result;
    }
    return std::string(buf, buf + n);
}

// Długość najdłuższego poprawnego prefiksu UTF-8 (aby nie emitować urwanych bajtów).
static size_t utf8_valid_prefix_len(const std::string& s) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        unsigned char c = (unsigned char) s[i];
        size_t len;
        if      (c < 0x80) len = 1;
        else if ((c >> 5) == 0x6) len = 2;
        else if ((c >> 4) == 0xE) len = 3;
        else if ((c >> 3) == 0x1E) len = 4;
        else { len = 1; } // nieprawidłowy bajt startowy — traktuj jako 1
        if (i + len > n) break; // niekompletny znak — zatrzymaj się tutaj
        i += len;
    }
    return i;
}

// Zdekodowanie listy tokenów. Ustawia logits tylko dla ostatniego tokenu.
// Zwraca wskaźnik na logity ostatniego tokenu lub nullptr przy błędzie.
static float* decode_tokens(const std::vector<llama_token>& tokens, int n_past) {
    const int n_batch = 512;
    int n = (int) tokens.size();
    if (n == 0) return nullptr;

    for (int start = 0; start < n; start += n_batch) {
        int cur = std::min(n_batch, n - start);
        bool last_chunk = (start + cur >= n);

        llama_batch batch = llama_batch_init(cur, 0, 1);
        for (int i = 0; i < cur; i++) {
            batch.token[i]     = tokens[start + i];
            batch.pos[i]       = n_past + start + i;
            batch.n_seq_id[i]  = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i]    = false;
        }
        if (last_chunk) {
            batch.logits[cur - 1] = true;
        }
        batch.n_tokens = cur;

        int rc = llama_decode(g_ctx, batch);
        llama_batch_free(batch);
        if (rc != 0) {
            LOGE("llama_decode zwrócił %d", rc);
            return nullptr;
        }
    }
    return llama_get_logits_ith(g_ctx, -1);
}

// Ręczny sampling z logitów.
static llama_token sample_token(float* logits,
                                const std::vector<llama_token>& recent,
                                float temp, float top_p, int top_k,
                                float repeat_penalty) {
    const int n_vocab = llama_n_vocab(g_model);

    // Kara za powtórzenia (repeat penalty) — na ostatnich ~64 tokenach.
    int repeat_last_n = 64;
    int from = std::max(0, (int) recent.size() - repeat_last_n);
    for (int i = from; i < (int) recent.size(); i++) {
        llama_token t = recent[i];
        if (t < 0 || t >= n_vocab) continue;
        if (logits[t] > 0) logits[t] /= repeat_penalty;
        else               logits[t] *= repeat_penalty;
    }

    struct Candidate { llama_token id; float logit; };
    std::vector<Candidate> cands;
    cands.reserve(n_vocab);
    for (int i = 0; i < n_vocab; i++) {
        cands.push_back({ (llama_token) i, logits[i] });
    }

    // Greedy jeśli temperatura <= 0.
    if (temp <= 0.0f) {
        auto best = std::max_element(cands.begin(), cands.end(),
            [](const Candidate& a, const Candidate& b) { return a.logit < b.logit; });
        return best->id;
    }

    // Sortuj malejąco wg logitów.
    std::sort(cands.begin(), cands.end(),
        [](const Candidate& a, const Candidate& b) { return a.logit > b.logit; });

    // Top-K.
    if (top_k > 0 && top_k < (int) cands.size()) {
        cands.resize(top_k);
    }

    // Softmax z temperaturą.
    float max_logit = cands[0].logit;
    double sum = 0.0;
    std::vector<double> probs(cands.size());
    for (size_t i = 0; i < cands.size(); i++) {
        double p = std::exp((cands[i].logit - max_logit) / temp);
        probs[i] = p;
        sum += p;
    }
    for (size_t i = 0; i < probs.size(); i++) probs[i] /= sum;

    // Top-P (nucleus).
    if (top_p < 1.0f) {
        double cum = 0.0;
        size_t cutoff = probs.size();
        for (size_t i = 0; i < probs.size(); i++) {
            cum += probs[i];
            if (cum >= top_p) { cutoff = i + 1; break; }
        }
        cands.resize(cutoff);
        probs.resize(cutoff);
        double s = 0.0;
        for (double p : probs) s += p;
        for (double& p : probs) p /= s;
    }

    // Losowanie z rozkładu.
    std::discrete_distribution<int> dist(probs.begin(), probs.end());
    int idx = dist(g_rng);
    return cands[idx].id;
}

// ---------------------------------------------------------------------------
// Funkcje JNI
// ---------------------------------------------------------------------------
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_llamaagent_LlamaEngine_nativeInit(JNIEnv* env, jobject /*thiz*/,
                                           jstring modelPath, jint nThreads, jint nCtx) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }

    // Zwolnij poprzedni model jeśli istnieje.
    if (g_ctx)   { llama_free(g_ctx);        g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::string model_path(path ? path : "");
    env->ReleaseStringUTFChars(modelPath, path);

    LOGI("Ładowanie modelu: %s (threads=%d, ctx=%d)", model_path.c_str(), (int)nThreads, (int)nCtx);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-only na Androidzie

    g_model = llama_load_model_from_file(model_path.c_str(), mparams);
    if (!g_model) {
        LOGE("Nie udało się załadować modelu: %s", model_path.c_str());
        return JNI_FALSE;
    }

    g_n_threads = (int) nThreads;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx         = (uint32_t) nCtx;
    cparams.n_threads     = nThreads;
    cparams.n_threads_batch = nThreads;
    cparams.n_batch       = 512;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Nie udało się utworzyć kontekstu");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_n_ctx = (int) llama_n_ctx(g_ctx);
    LOGI("Model załadowany. n_ctx=%d", g_n_ctx);
    return JNI_TRUE;
}

// Wspólny rdzeń generacji. Jeśli callback != nullptr, tokeny są przesyłane
// strumieniowo; w przeciwnym razie akumulowane i zwracane jako całość.
static std::string run_generation(JNIEnv* env, const std::string& prompt,
                                  int maxTokens, float temp, float top_p, int top_k,
                                  float repeat_penalty, jobject callback,
                                  jmethodID onToken) {
    std::string output;

    std::vector<llama_token> tokens = tokenize(prompt, true);
    if (tokens.empty()) {
        return output;
    }
    if ((int) tokens.size() >= g_n_ctx) {
        // Przytnij prompt do rozmiaru kontekstu (zostaw miejsce na generację).
        int keep = g_n_ctx - 8;
        if (keep < 1) keep = 1;
        tokens.erase(tokens.begin(), tokens.end() - std::min((int)tokens.size(), keep));
    }

    llama_kv_cache_clear(g_ctx);

    float* logits = decode_tokens(tokens, 0);
    if (!logits) return output;

    std::vector<llama_token> recent = tokens;
    int n_past = (int) tokens.size();
    llama_token eos = llama_token_eos(g_model);

    std::string pending; // bufor bajtów dla poprawnego UTF-8

    for (int i = 0; i < maxTokens; i++) {
        if (n_past >= g_n_ctx) break;

        llama_token id = sample_token(logits, recent, temp, top_p, top_k, repeat_penalty);
        if (id == eos) break;

        pending += token_to_piece(id);
        size_t valid = utf8_valid_prefix_len(pending);
        if (valid > 0) {
            std::string chunk = pending.substr(0, valid);
            pending.erase(0, valid);
            output += chunk;
            if (callback && onToken) {
                jstring jchunk = env->NewStringUTF(chunk.c_str());
                env->CallVoidMethod(callback, onToken, jchunk);
                env->DeleteLocalRef(jchunk);
                if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
            }
        }

        recent.push_back(id);

        std::vector<llama_token> next = { id };
        logits = decode_tokens(next, n_past);
        if (!logits) break;
        n_past++;
    }

    return output;
}

JNIEXPORT jstring JNICALL
Java_com_llamaagent_LlamaEngine_nativeGenerate(JNIEnv* env, jobject /*thiz*/,
                                               jstring prompt, jint maxTokens, jfloat temp,
                                               jfloat topP, jint topK, jfloat repeatPenalty) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx || !g_model) {
        return env->NewStringUTF("");
    }
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(p ? p : "");
    env->ReleaseStringUTFChars(prompt, p);

    std::string out = run_generation(env, prompt_str, (int)maxTokens, (float)temp,
                                     (float)topP, (int)topK, (float)repeatPenalty,
                                     nullptr, nullptr);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_llamaagent_LlamaEngine_nativeGenerateStream(JNIEnv* env, jobject /*thiz*/,
                                                     jstring prompt, jint maxTokens, jfloat temp,
                                                     jfloat topP, jint topK, jfloat repeatPenalty,
                                                     jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken    = env->GetMethodID(cbClass, "onToken",    "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onError    = env->GetMethodID(cbClass, "onError",    "(Ljava/lang/String;)V");

    if (!g_ctx || !g_model) {
        if (onError) {
            jstring msg = env->NewStringUTF("Model nie jest załadowany");
            env->CallVoidMethod(callback, onError, msg);
            env->DeleteLocalRef(msg);
        }
        return;
    }

    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(p ? p : "");
    env->ReleaseStringUTFChars(prompt, p);

    run_generation(env, prompt_str, (int)maxTokens, (float)temp,
                   (float)topP, (int)topK, (float)repeatPenalty,
                   callback, onToken);

    if (onComplete) {
        env->CallVoidMethod(callback, onComplete);
    }
}

JNIEXPORT void JNICALL
Java_com_llamaagent_LlamaEngine_nativeFree(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx)   { llama_free(g_ctx);        g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
    g_n_ctx = 0;
    LOGI("Zasoby modelu zwolnione");
}

JNIEXPORT jint JNICALL
Java_com_llamaagent_LlamaEngine_nativeGetContextSize(JNIEnv* /*env*/, jobject /*thiz*/) {
    return (jint) g_n_ctx;
}

JNIEXPORT jboolean JNICALL
Java_com_llamaagent_LlamaEngine_nativeIsLoaded(JNIEnv* /*env*/, jobject /*thiz*/) {
    return (g_ctx != nullptr && g_model != nullptr) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
