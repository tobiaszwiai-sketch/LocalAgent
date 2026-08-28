package com.llamaagent.agent

import com.google.gson.JsonParser
import com.llamaagent.LlamaEngine
import com.llamaagent.agent.tools.AgentTool
import com.llamaagent.data.AppSettings
import com.llamaagent.data.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Silnik agentowy: uruchamia pętlę rozumowania z możliwością wywoływania narzędzi.
 *
 * Model komunikuje chęć użycia narzędzia poprzez blok:
 *   <tool_call>
 *   <tool_name>NAZWA</tool_name>
 *   <parameters>{"param":"value"}</parameters>
 *   </tool_call>
 *
 * Wynik trafia z powrotem do modelu jako <tool_result>...</tool_result>.
 */
class AgentEngine(
    private val engine: LlamaEngine,
    private val tools: List<AgentTool>
) {
    private val toolMap = tools.associateBy { it.name }

    fun buildSystemPrompt(): String {
        val toolList = tools.joinToString("\n") { "- ${it.description}" }
        return """
Jesteś pomocnym asystentem AI z dostępem do narzędzi, działającym lokalnie na smartfonie.
Gdy potrzebujesz użyć narzędzia, wypisz DOKŁADNIE taki blok (i nic więcej w tej turze):
<tool_call>
<tool_name>NAZWA_NARZĘDZIA</tool_name>
<parameters>{"param": "value"}</parameters>
</tool_call>

Dostępne narzędzia:
$toolList

Zasady:
- Używaj narzędzi tylko wtedy, gdy są naprawdę potrzebne (np. aktualne fakty, obliczenia, dane z urządzenia).
- Po otrzymaniu <tool_result> przeanalizuj wynik i albo użyj kolejnego narzędzia, albo udziel ostatecznej odpowiedzi.
- Ostateczną odpowiedź napisz normalnym tekstem, BEZ bloku <tool_call>.
- Odpowiadaj w języku użytkownika (domyślnie po polsku).
""".trim()
    }

    /**
     * Uruchamia pętlę agentową.
     * @param onTrace informuje o wewnętrznych krokach (wywołania narzędzi i wyniki)
     * @param onToken strumień tokenów ostatecznej odpowiedzi
     * @return pełny tekst ostatecznej odpowiedzi
     */
    suspend fun run(
        userMessage: String,
        history: List<ChatMessage>,
        settings: AppSettings,
        onTrace: (String) -> Unit,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.Default) {
        val turns = PromptBuilder.historyToTurns(history).toMutableList()
        turns.add(PromptBuilder.Turn("user", userMessage))

        val systemPrompt = buildSystemPrompt()
        val maxIter = settings.maxAgentIterations.coerceIn(1, 10)

        for (iter in 0 until maxIter) {
            val prompt = PromptBuilder.build(systemPrompt, turns)
            // Najpierw generujemy w całości, by wykryć ewentualne wywołanie narzędzia.
            val generated = generateBlocking(prompt, settings) { /* brak strumienia na etapie decyzji */ }

            val toolCall = parseToolCall(generated)
            if (toolCall == null) {
                // Brak wywołania narzędzia — to jest ostateczna odpowiedź.
                val clean = stripToolTags(generated).trim()
                // Wyślij strumieniowo do UI, aby zachować efekt "pisania".
                emitAsStream(clean, onToken)
                return@withContext clean
            }

            // Zarejestruj turę asystenta z wywołaniem narzędzia.
            turns.add(PromptBuilder.Turn("assistant", generated.trim()))
            onTrace("\uD83D\uDD27 Narzędzie: ${toolCall.name}(${toolCall.rawParams})")

            val tool = toolMap[toolCall.name]
            val result = if (tool == null) {
                "Błąd: nieznane narzędzie '${toolCall.name}'."
            } else {
                try {
                    tool.execute(toolCall.params)
                } catch (e: Exception) {
                    "Błąd wykonania narzędzia: ${e.message}"
                }
            }
            onTrace("\u2705 Wynik: ${result.take(300)}${if (result.length > 300) "..." else ""}")

            turns.add(PromptBuilder.Turn("user", "<tool_result>\n$result\n</tool_result>"))
        }

        // Przekroczono limit iteracji — wygeneruj końcową odpowiedź na podstawie zebranych danych.
        onTrace("\u23F3 Osiągnięto limit iteracji — generuję odpowiedź na podstawie zebranych informacji.")
        turns.add(PromptBuilder.Turn("user",
            "Na podstawie powyższych wyników udziel teraz ostatecznej odpowiedzi bez używania narzędzi."))
        val prompt = PromptBuilder.build(systemPrompt, turns)
        val finalText = generateBlocking(prompt, settings) { }
        val clean = stripToolTags(finalText).trim()
        emitAsStream(clean, onToken)
        clean
    }

    // --- pomocnicze ---------------------------------------------------------

    private suspend fun generateBlocking(
        prompt: String,
        settings: AppSettings,
        onToken: (String) -> Unit
    ): String {
        val deferred = CompletableDeferred<String>()
        val sb = StringBuilder()
        engine.nativeGenerateStream(
            prompt,
            settings.maxTokens,
            settings.temperature,
            settings.topP,
            settings.topK,
            settings.repeatPenalty,
            object : LlamaEngine.TokenCallback {
                override fun onToken(token: String) {
                    sb.append(token)
                    onToken(token)
                }
                override fun onComplete() { deferred.complete(sb.toString()) }
                override fun onError(error: String) { deferred.complete("[Błąd generacji: $error]") }
            }
        )
        return deferred.await()
    }

    private fun emitAsStream(text: String, onToken: (String) -> Unit) {
        // Dziel na krótkie fragmenty, aby UI aktualizowało się płynnie.
        var i = 0
        val step = 6
        while (i < text.length) {
            val end = minOf(i + step, text.length)
            onToken(text.substring(i, end))
            i = end
        }
    }

    data class ToolCall(val name: String, val params: Map<String, Any?>, val rawParams: String)

    private fun parseToolCall(text: String): ToolCall? {
        val callRegex = Regex("<tool_call>([\\s\\S]*?)</tool_call>", RegexOption.IGNORE_CASE)
        val match = callRegex.find(text) ?: return null
        val inner = match.groupValues[1]

        val name = Regex("<tool_name>([\\s\\S]*?)</tool_name>", RegexOption.IGNORE_CASE)
            .find(inner)?.groupValues?.get(1)?.trim() ?: return null

        val rawParams = Regex("<parameters>([\\s\\S]*?)</parameters>", RegexOption.IGNORE_CASE)
            .find(inner)?.groupValues?.get(1)?.trim() ?: "{}"

        val params = parseJsonParams(rawParams)
        return ToolCall(name, params, rawParams)
    }

    private fun parseJsonParams(raw: String): Map<String, Any?> {
        return try {
            val obj = JsonParser.parseString(raw).asJsonObject
            obj.entrySet().associate { (k, v) ->
                k to when {
                    v.isJsonNull -> null
                    v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> v.asDouble
                    v.isJsonPrimitive && v.asJsonPrimitive.isBoolean -> v.asBoolean
                    else -> v.asString
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun stripToolTags(text: String): String {
        return text
            .replace(Regex("<tool_call>[\\s\\S]*?</tool_call>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</?tool_result>", RegexOption.IGNORE_CASE), "")
            .replace("<|im_end|>", "")
            .trim()
    }
}
