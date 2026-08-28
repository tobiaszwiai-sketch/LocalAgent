package com.llamaagent.agent.tools

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Odczyt plików tekstowych z pamięci urządzenia.
 *
 * Dla bezpieczeństwa dozwolone są tylko lokalizacje publiczne (Download,
 * Documents, katalog modeli). Wymaga uprawnień odczytu pamięci.
 */
class FileAccessTool : AgentTool {
    override val name = "read_file"
    override val description = "read_file(path: string) - Odczytuje plik tekstowy z pamięci telefonu (np. z katalogu Download)"

    private val maxBytes = 16 * 1024 // 16 KB — limit żeby nie zapchać kontekstu

    override suspend fun execute(params: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val path = (params["path"] ?: params["file"])?.toString()?.trim().orEmpty()
        if (path.isEmpty()) return@withContext "Błąd: brak parametru 'path'."

        try {
            val file = resolveFile(path)
            when {
                file == null -> "Błąd: dostęp do tej ścieżki nie jest dozwolony."
                !file.exists() -> "Błąd: plik nie istnieje: ${file.absolutePath}"
                file.isDirectory -> {
                    val listing = file.listFiles()?.joinToString("\n") { child ->
                        val kind = if (child.isDirectory) "[katalog]" else "[plik ${child.length()} B]"
                        "- ${child.name} $kind"
                    } ?: "(pusty katalog)"
                    "Zawartość katalogu ${file.absolutePath}:\n$listing"
                }
                !file.canRead() -> "Błąd: brak uprawnień do odczytu pliku."
                else -> {
                    val bytes = file.readBytes()
                    val text = String(bytes.copyOf(minOf(bytes.size, maxBytes)))
                    val truncated = if (bytes.size > maxBytes) "\n...[obcięto, plik ma ${bytes.size} B]" else ""
                    "Zawartość ${file.name}:\n$text$truncated"
                }
            }
        } catch (e: Exception) {
            "Błąd odczytu pliku: ${e.message}"
        }
    }

    private fun resolveFile(path: String): File? {
        val f = File(path)
        val absolute = if (f.isAbsolute) f else File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), path
        )
        val canonical = try { absolute.canonicalFile } catch (e: Exception) { return null }

        val allowedRoots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStorageDirectory()
        ).map { it.canonicalFile.absolutePath }

        val ok = allowedRoots.any { canonical.absolutePath.startsWith(it) }
        return if (ok) canonical else null
    }
}
