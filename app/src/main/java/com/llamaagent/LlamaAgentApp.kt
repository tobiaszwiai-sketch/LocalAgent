package com.llamaagent

import android.app.Application
import android.util.Log

/**
 * Klasa Application — inicjalizuje backendy GGML (CPU) przy starcie procesu.
 *
 * Backendy muszą być załadowane ZANIM cokolwiek wywoła nativeInit(),
 * bo libggml.so nie potrafi samodzielnie znaleźć libggml-cpu-android_*.so
 * na Androidzie (szukałoby przez /proc/self/exe zamiast przez nativeLibraryDir).
 */
class LlamaAgentApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // nativeLibraryDir = np. /data/app/com.llamaagent-xxx/lib/arm64
        // Tam Android rozpakowuje wszystkie .so z APK.
        val nativeLibDir = applicationInfo.nativeLibraryDir
        Log.i("LlamaAgentApp", "Inicjalizacja backendów GGML z: $nativeLibDir")

        try {
            LlamaEngine.nativeInitBackends(nativeLibDir)
        } catch (e: Throwable) {
            Log.e("LlamaAgentApp", "Błąd inicjalizacji backendów: ${e.message}")
        }
    }
}
