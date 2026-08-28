package com.llamaagent.data

/**
 * Konfiguracja generacji i agenta. Wartości są utrwalane w DataStore.
 */
data class AppSettings(
    val temperature: Float = 0.7f,
    val contextLength: Int = 4096,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val threads: Int = 4,
    val maxTokens: Int = 1024,
    val agentMode: Boolean = false,
    val maxAgentIterations: Int = 5
) {
    companion object {
        val DEFAULT = AppSettings()
        val CONTEXT_OPTIONS = listOf(512, 1024, 2048, 4096, 8192)
    }
}
