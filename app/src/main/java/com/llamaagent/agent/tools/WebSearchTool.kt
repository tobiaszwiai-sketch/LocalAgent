package com.llamaagent.agent.tools

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Wyszukiwanie w sieci przez DuckDuckGo Instant Answer API.
 */
class WebSearchTool : AgentTool {
    override val name = "web_search"
    override val description = "web_search(query: string) - Wyszukuje informacje w sieci (DuckDuckGo)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(params: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val query = (params["query"] ?: params["q"])?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return@withContext "Błąd: brak parametru 'query'."

        try {
            val url = "https://api.duckduckgo.com/?q=" +
                URLEncoder.encode(query, "UTF-8") +
                "&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LlamaAgentAndroid/1.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext "Błąd wyszukiwania: HTTP ${resp.code}"
                }
                val body = resp.body?.string().orEmpty()
                parseResults(body, query)
            }
        } catch (e: Exception) {
            "Błąd połączenia z wyszukiwarką: ${e.message}"
        }
    }

    private fun parseResults(json: String, query: String): String {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val sb = StringBuilder()

            val abstract = root.get("AbstractText")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            if (abstract.isNotBlank()) {
                sb.append("Podsumowanie: ").append(abstract).append("\n")
            }
            val answer = root.get("Answer")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            if (answer.isNotBlank()) {
                sb.append("Odpowiedź: ").append(answer).append("\n")
            }

            val related = root.getAsJsonArray("RelatedTopics")
            if (related != null) {
                var count = 0
                for (el in related) {
                    if (count >= 5) break
                    val obj = el.asJsonObject
                    val text = obj.get("Text")?.takeIf { !it.isJsonNull }?.asString
                    if (!text.isNullOrBlank()) {
                        sb.append("- ").append(text).append("\n")
                        count++
                    }
                }
            }

            if (sb.isBlank()) {
                "Brak bezpośrednich wyników dla zapytania: \"$query\". Spróbuj innego sformułowania."
            } else {
                sb.toString().trim()
            }
        } catch (e: Exception) {
            "Nie udało się przetworzyć wyników wyszukiwania: ${e.message}"
        }
    }
}
