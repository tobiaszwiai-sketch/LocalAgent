package com.llamaagent.agent

import com.llamaagent.data.ChatMessage
import com.llamaagent.data.Role

/**
 * Budowanie promptu w formacie ChatML (używanym przez modele Qwen).
 *
 * Format:
 * <|im_start|>system\n...<|im_end|>\n
 * <|im_start|>user\n...<|im_end|>\n
 * <|im_start|>assistant\n
 */
object PromptBuilder {

    data class Turn(val role: String, val content: String)

    fun build(systemPrompt: String, turns: List<Turn>): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")
        }
        for (t in turns) {
            sb.append("<|im_start|>").append(t.role).append("\n")
                .append(t.content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    /** Konwersja historii czatu na tury ChatML (pomija wpisy śladu narzędzi). */
    fun historyToTurns(history: List<ChatMessage>): List<Turn> {
        return history.filter { !it.isToolTrace && it.role != Role.SYSTEM }
            .map { msg ->
                val role = when (msg.role) {
                    Role.USER -> "user"
                    Role.ASSISTANT -> "assistant"
                    Role.TOOL -> "user"
                    Role.SYSTEM -> "system"
                }
                Turn(role, msg.content)
            }
    }
}
