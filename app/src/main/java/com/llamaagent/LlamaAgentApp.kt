package com.llamaagent

import android.app.Application
import android.util.Log
import java.io.File

/**
 * Klasa Application — inicjalizuje backendy GGML (CPU) przy starcie procesu.
 *
 * Problem na Androidzie: dlopen() wywołany z kodu natywnego (libggml.so) jest
 * blokowany przez linker namespace — Android nie pozwala załadować nowych .so
 * z poziomu C bez uprzedniego załadowania przez ClassLoader Javy.
 *
 * Rozwiązanie:
 * 1. Załaduj każdy libggml-cpu-android_*.so przez System.load(fullPath) w Kotlinie
 *    -> Android rejestruje je we właściwym namespace.
 * 2. Wywołaj nativeInitBackends() w JNI, które użyje dlsym(RTLD_DEFAULT)
 *    żeby znaleźć ggml_backend_cpu_reg() i ręcznie zarejestruje backend.
 */
class LlamaAgentApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // nativeLibDir = np. /data/app/com.llamaagent-xxx/lib/arm64
        val nativeLibDir = applicationInfo.nativeLibraryDir
        Log.i("LlamaAgentApp", "nativeLibDir: $nativeLibDir")

        // Krok 1: załaduj CPU backendy przez System.load() — Java ClassLoader
        // zna właściwy namespace, dlopen() z C nie.
        val cpuLibs = File(nativeLibDir).listFiles()
            ?.filter { it.name.startsWith("libggml-cpu") && it.name.endsWith(".so") }
            ?.sortedByDescending { it.name } // wyższe warianty ARM najpierw
            ?: emptyList()

        var loadedCount = 0
        for (f in cpuLibs) {
            try {
                System.load(f.absolutePath)
                Log.i("LlamaAgentApp", "CPU backend załadowany: ${f.name}")
                loadedCount++
            } catch (e: UnsatisfiedLinkError) {
                Log.w("LlamaAgentApp", "Pominięto (CPU nie wspiera): ${f.name}")
            } catch (e: Throwable) {
                Log.e("LlamaAgentApp", "Blad ladowania ${f.name}: ${e.message}")
            }
        }
        Log.i("LlamaAgentApp", "Zaladowano $loadedCount z ${cpuLibs.size} wariantow CPU")

        // Krok 2: zarejestruj backendy w rejestrze GGML (przez dlsym)
        try {
            LlamaEngine.nativeInitBackends(nativeLibDir)
        } catch (e: Throwable) {
            Log.e("LlamaAgentApp", "Blad nativeInitBackends: ${e.message}")
        }
    }
}
