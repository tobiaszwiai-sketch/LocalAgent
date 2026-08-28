package com.llamaagent.agent.tools

/**
 * Wspólny interfejs narzędzia agentowego.
 */
interface AgentTool {
    /** Nazwa narzędzia używana w tagu <tool_name>. */
    val name: String

    /** Krótki opis + sygnatura do system promptu. */
    val description: String

    /**
     * Wykonuje narzędzie. [params] to sparsowana mapa parametrów z JSON.
     * Zwraca wynik jako tekst (trafi do <tool_result>).
     * Metoda może być blokująca — jest wywoływana z dispatchera IO.
     */
    suspend fun execute(params: Map<String, Any?>): String
}
