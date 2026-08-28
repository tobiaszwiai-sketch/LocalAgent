package com.llamaagent

/**
 * Kotlinowy wrapper na natywną bibliotekę llama.cpp (llama_jni.cpp).
 *
 * Wszystkie metody `native*` są blokujące — należy je wywoływać z coroutine
 * na dispatcherze IO / Default, nigdy z głównego wątku UI.
 */
class LlamaEngine {

    external fun nativeInit(modelPath: String, nThreads: Int, nCtx: Int): Boolean

    external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ): String

    external fun nativeGenerateStream(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenCallback
    )

    external fun nativeFree()

    external fun nativeGetContextSize(): Int

    external fun nativeIsLoaded(): Boolean

    /** Callback wywoływany z natywnego kodu podczas generacji strumieniowej. */
    interface TokenCallback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(error: String)
    }

    companion object {
        init {
            System.loadLibrary("llamaagent")
        }
    }
}
