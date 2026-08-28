package com.llamaagent.data

/**
 * Profil modelu GGUF. Zawiera metadane oraz domyślne parametry uruchomieniowe.
 */
data class ModelProfile(
    val id: String,
    val name: String,
    val description: String,
    val recommendedForRam: Int,   // MB
    val quantization: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeGB: Double,
    val defaultNCtx: Int,
    val defaultNThreads: Int
) {
    val isCustom: Boolean get() = id == "custom"
}

/**
 * Zestaw wbudowanych profili modeli możliwych do wyboru w aplikacji.
 * Modele nie są dołączone do APK (są zbyt duże) — pobiera się je ręcznie
 * i umieszcza w katalogu Download/LlamaAgent/ (patrz Menedżer modeli).
 */
val PRESET_MODELS: List<ModelProfile> = listOf(
    ModelProfile(
        id = "qwen3_1.7b",
        name = "Qwen 3 1.7B (Q4_K_M)",
        description = "Bardzo szybki, do 12GB RAM",
        recommendedForRam = 4096,
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
        fileName = "qwen3-1.7b-q4_k_m.gguf",
        sizeGB = 1.1,
        defaultNCtx = 4096,
        defaultNThreads = 4
    ),
    ModelProfile(
        id = "qwen3_4b",
        name = "Qwen 3 4B (Q4_K_M)",
        description = "Dobry balans szybkości i jakości",
        recommendedForRam = 6144,
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
        fileName = "qwen3-4b-q4_k_m.gguf",
        sizeGB = 2.6,
        defaultNCtx = 4096,
        defaultNThreads = 4
    ),
    ModelProfile(
        id = "qwen3_8b",
        name = "Qwen 3 8B (Q4_K_M)",
        description = "Najlepszy dla telefonów z 12GB RAM (np. S26 Ultra)",
        recommendedForRam = 8192,
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen3-8B-GGUF/resolve/main/Qwen3-8B-Q4_K_M.gguf",
        fileName = "qwen3-8b-q4_k_m.gguf",
        sizeGB = 5.0,
        defaultNCtx = 4096,
        defaultNThreads = 4
    ),
    ModelProfile(
        id = "qwen25_7b",
        name = "Qwen 2.5 7B Instruct (Q4_K_M)",
        description = "Stabilna alternatywa, sprawdzona jakość",
        recommendedForRam = 8192,
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
        fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
        sizeGB = 4.7,
        defaultNCtx = 4096,
        defaultNThreads = 4
    ),
    ModelProfile(
        id = "qwen3_32b",
        name = "Qwen 3 32B (Q2_K) \u26A0\uFE0F",
        description = "Eksperymentalny, tylko telefony z 16GB+ RAM",
        recommendedForRam = 14336,
        quantization = "Q2_K",
        downloadUrl = "https://huggingface.co/Qwen/Qwen3-32B-GGUF/resolve/main/Qwen3-32B-Q2_K.gguf",
        fileName = "qwen3-32b-q2_k.gguf",
        sizeGB = 12.3,
        defaultNCtx = 2048,
        defaultNThreads = 4
    ),
    ModelProfile(
        id = "custom",
        name = "Własny model (GGUF)",
        description = "Załaduj dowolny plik .gguf z pamięci telefonu",
        recommendedForRam = 0,
        quantization = "Custom",
        downloadUrl = "",
        fileName = "",
        sizeGB = 0.0,
        defaultNCtx = 2048,
        defaultNThreads = 4
    )
)
