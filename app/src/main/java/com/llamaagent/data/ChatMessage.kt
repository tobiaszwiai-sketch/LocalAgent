package com.llamaagent.data

import java.util.UUID

enum class Role { USER, ASSISTANT, SYSTEM, TOOL }

/**
 * Pojedyncza wiadomość w konwersacji.
 *
 * @param streaming true, gdy treść jest aktualnie strumieniowana (token po tokenie)
 * @param isToolTrace true dla wpisów pokazujących wewnętrzne działanie agenta
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val streaming: Boolean = false,
    val isToolTrace: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
