package com.llamaagent.data

import android.os.Environment
import java.io.File

/**
 * Lokalizowanie plików modeli GGUF w pamięci telefonu.
 *
 * Konwencja: pliki umieszcza się w  <Download>/LlamaAgent/
 */
object ModelStorage {

    const val SUBDIR = "LlamaAgent"

    /** Katalog Download/LlamaAgent/ (tworzony w razie potrzeby). */
    fun modelsDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Zwraca plik dla danego profilu lub null, jeśli nie istnieje. */
    fun resolveModelFile(profile: ModelProfile): File? {
        if (profile.isCustom || profile.fileName.isBlank()) return null
        val candidates = listOf(
            File(modelsDir(), profile.fileName),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), profile.fileName)
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    fun isDownloaded(profile: ModelProfile): Boolean = resolveModelFile(profile) != null

    fun expectedPath(profile: ModelProfile): String =
        File(modelsDir(), profile.fileName).absolutePath

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) return String.format("%.2f GB", gb)
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }
}
