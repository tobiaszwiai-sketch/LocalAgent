package com.llamaagent.data

import android.os.Environment
import java.io.File

/**
 * Lokalizowanie plików modeli GGUF w pamięci telefonu.
 *
 * Szukamy w kilku miejscach (case-insensitive):
 *  1. Download/LlamaAgent/<fileName>
 *  2. Download/<fileName>
 *  3. Download/LlamaAgent/<dowolny plik .gguf zawierający klucz modelu>
 *  4. Download/<dowolny plik .gguf zawierający klucz modelu>
 */
object ModelStorage {

    const val SUBDIR = "LlamaAgent"

    /** Katalog Download/LlamaAgent/ */
    fun modelsDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Katalog Download/ */
    private fun downloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /**
     * Zwraca plik dla danego profilu lub null, jeśli nie istnieje.
     * Wyszukiwanie jest case-insensitive i obsługuje nazwy z oryginalnego URL (np. "Qwen3-1.7B-Q4_K_M.gguf").
     */
    fun resolveModelFile(profile: ModelProfile): File? {
        if (profile.isCustom || profile.fileName.isBlank()) return null

        val searchDirs = listOf(modelsDir(), downloadsDir())

        // 1. Dokładne dopasowanie (case-insensitive)
        for (dir in searchDirs) {
            if (!dir.canRead()) continue
            val exact = File(dir, profile.fileName)
            if (exact.exists() && exact.length() > 0) return exact

            // Case-insensitive scan
            dir.listFiles()?.forEach { f ->
                if (f.name.equals(profile.fileName, ignoreCase = true) && f.length() > 0)
                    return f
            }
        }

        // 2. Elastyczne dopasowanie po słowach kluczowych z nazwy pliku
        // np. "qwen3-1.7b-q4_k_m.gguf" → klucze: ["1.7b", "q4_k_m"]
        val keys = extractModelKeys(profile.fileName)
        if (keys.isNotEmpty()) {
            for (dir in searchDirs) {
                if (!dir.canRead()) continue
                dir.listFiles()?.forEach { f ->
                    if (!f.name.endsWith(".gguf", ignoreCase = true)) return@forEach
                    if (keys.all { key -> f.name.contains(key, ignoreCase = true) } && f.length() > 0)
                        return f
                }
            }
        }

        return null
    }

    /** Wyciąga kluczowe słowa z nazwy pliku do elastycznego dopasowania. */
    private fun extractModelKeys(fileName: String): List<String> {
        val base = fileName.removeSuffix(".gguf").lowercase()
        // Wyciągnij rozmiar (np. "1.7b", "4b", "8b") i kwantyzację (np. "q4_k_m")
        val sizePattern = Regex("""(\d+\.?\d*b)""")
        val quantPattern = Regex("""(q\d+_k_[ms]|q\d+_\d)""")
        return buildList {
            sizePattern.find(base)?.groupValues?.getOrNull(1)?.let { add(it) }
            quantPattern.find(base)?.groupValues?.getOrNull(1)?.let { add(it) }
        }
    }

    fun isDownloaded(profile: ModelProfile): Boolean = resolveModelFile(profile) != null

    fun expectedPath(profile: ModelProfile): String =
        File(modelsDir(), profile.fileName).absolutePath

    /** Zwraca listę wszystkich znalezionych plików GGUF (do diagnostyki). */
    fun listAllGgufFiles(): List<File> {
        val dirs = listOf(modelsDir(), downloadsDir())
        return dirs.flatMap { dir ->
            if (dir.canRead())
                dir.listFiles()?.filter { it.name.endsWith(".gguf", ignoreCase = true) && it.length() > 0 } ?: emptyList()
            else emptyList()
        }.distinctBy { it.absolutePath }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) return String.format("%.2f GB", gb)
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }
}
